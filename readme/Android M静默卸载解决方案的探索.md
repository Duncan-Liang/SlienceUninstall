# Android M静默卸载解决方案的探索
------
一般情况下，Android系统安装apk会出现一个安装界面，用户可以点击确定或者取消来进行apk的安装。但在实际项目中，有一种需求就是希望apk在后台安装（不出现安装界面的提示），这种安装方式称为静默安装，同样，apk在后台卸载（不出现卸载界面的提示），这种卸载方式称为静默卸载。本篇章暂只讨论与静默卸载相关的情况，基于Android M系统源码分析。

## Android应用卸载权限
------
```java
frameworks\base\core\res\AndroidManifest.xml
<!-- @SystemApi Allows an application to delete packages.<p>Not for use by third-party applications. -->
<permission android:name="android.permission.DELETE_PACKAGES"android:protectionLevel="signature|privileged" />
```
从这个卸载应用的权限来看，需要apk需要具有系统签名,或者签名与平台不一致的情况下，应用内置在system/priv-app目录下。否则，否则PackageManager会拒绝分配给你权限。（有兴趣的同学可以参见：PackageManagerService 的 grantSignaturePermission 方法）。加入priv-app方法：
```java
在Android.mk中增加 LOCAL_PRIVILEGED_MODULE := true
```
目前遇到比较多的情况是，目前Android手机会内置部分自己研发的应用（launcher等应用）到system/priv-app目录，但是签名与系统签名不一致（大概是由于此类应用适配的机型以及平台比较多，由于提供的应用后台要自动升级，不可能保持签名与系统签名一致）。内置在system/priv-app目录，的确可以获取到DELETE_PACKAGES权限。可以在设备的/data/system/packages.xml目录下，看到如下信息：
```java
<package name="包名"codePath="/system/priv-app/应用名"
<item name="android.permission.DELETE_PACKAGES" granted="true" flags="0" />
```
由于应用经常有需求更新，后台请求更新后，codepath更改为/data/app目录了。
这里与应用自动更新方式有关系，这里就不再阐述，后续章节会更新此类问题。
```java
package name="包名" codePath="/data/app/应用名"
<item name="android.permission.DELETE_PACKAGES" granted="true" flags="0" />
```
可以发现，内置在system/priv-app目录下的应用，自动更新之后，即使目录已经变更到data/app应用安装目录之后，应用获取的权限并没有更改。这样更新过后，在data目录下的应用，也可以具有相应卸载应用的权限了。
至于保持签名与系统签名一致，这条对于需要拿到设置system的签名的相关文件，对应用进行签名操作，具体如下
```java
java -jar signapk.jar platform.x509.pem platform.pk8 应用.apk 应用sign.apk
```
然后用adb install安装应用，查看对应的data/system/packages.xml文件，同样可以看到如下信息：
```java
package name="包名" codePath="/data/app/应用名"
<sigs count="1">
    <cert index="0" />
</sigs>
<item name="android.permission.DELETE_PACKAGES" granted="true" flags="0" />
```
不过目前如果应用开发要获取系统签名还是比较麻烦，系统开发工作者比较容易或者到，具体此篇就不做介绍了。

## Android应用卸载接口
------
查看Android API文档，在API 21添加了android.content.pm.PackageInstaller，具体可以参考https://developer.android.com/reference/android/content/pm/PackageInstaller.html https://developer.android.com/reference/android/content/pm/PackageInstaller.Session.html。可以看到如下信息：
```java
uninstall(String packageName, IntentSender statusReceiver)
Uninstall the given package, removing it completely from the device.
```
由于非系统源码开发，无法调用到PackageManager.java中的deletePackage方法。
```java
// @hide
// @SystemApi
public abstract void deletePackage(
        String packageName, IPackageDeleteObserver observer, int flags);
```
暂时就不考虑通过PackageManager中的deletePackage来实现此需求。
在framework查看此接口的具体实现，最终会响应到PackageInstallerService的uninstall方法。
```java
frameworks\base\services\core\java\com\android\server\pm\PackageInstallerService.java
public void uninstall(String packageName, String callerPackageName, int flags,
            IntentSender statusReceiver, int userId) 
```
---
在Framework相关源码中，可以发现有两次调用此接口。
### 1. pm.java中runUninstall调用
Android终端提供了一些CMD，开发人员可以通过这些CMD获取信息和执行任务。pm命令主要用来安装，查询，卸载apk等任务。pm命令的实现，主要是通过Java层，并且封装形式是jar，这一点个人觉得很强大，也很神奇。对于可执行档，一般都是c/c++去实现，实现起来没有java灵活，现在java层也可以进行封装，实现交互的tool，google的这个设计和功能，确实非常强大。下面的runUninstall函数实现，主要是对pm uninstall命令的响应。
```java
frameworks\base\cmds\pm\src\com\android\commands\pm\Pm.java
private int runUninstall() throws RemoteException {
    ......
    mInstaller.uninstall(pkg, null /* callerPackageName */, flags,
            receiver.getIntentSender(), userId);
    ......
}
```

### 2.PackageInstaller.java中uninstall调用
似乎从 API 21 (Lollipop)开始google添加了PackageInstaller，提供了安装，更新，移除应用的能力。PackageInstallerService是此接口的具体实现。
```java
frameworks\base\core\java\android\content\pm\PackageInstaller.java
    public void uninstall(@NonNull String packageName,.....) {
        try {
            mInstaller.uninstall(packageName, mInstallerPackageName, 0, statusReceiver, mUserId);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }
```
从上面两点来看，卸载apk至少有两种方式实现静默安装的需求。

## PM命令实现静默安装
### 1. 直接adb shell 命令卸载应用
```java
adb shell pm uninstall -k (应用包名)
```
### 2. 在Android程序代码中实现pm卸载应用
```java
Runtime.getRuntime().exec("pm uninstall -k uninstallPackageName")
```
上述两种情况，均会在pm.java响应到。现在具体查看一下Pm.java中rununinstall的实现
```java
private int runUninstall() throws RemoteException {
                ......
    //先解析一下指令的属性
    while ((opt=nextOption()) != null) {
        if (opt.equals("-k")) {
                .......
        }
    }
    //获取卸载应用的包名
    String pkg = nextArg();
    if (pkg == null) {
        System.err.println("Error: no package specified");
        showUsage();
        return 1;
    }
                ......
    if (userId == UserHandle.USER_ALL) {
    //通过pkg判断设置flag
    }
                ......
    //调用uninstall具体卸载应用，这里注意的是callerPackageName设为null
    mInstaller.uninstall(pkg, null /* callerPackageName */, flags,
            receiver.getIntentSender(), userId);
                ......
}
```
PackageInstallerService.java中uninstall的实现
```java
public void uninstall(String packageName, .....) {
    final int callingUid = Binder.getCallingUid();
    mPm.enforceCrossUserPermission(callingUid, userId, true, true, "uninstall");
    //操作卸载的应用对应的uid如果是shell 或者root uid
    //均不做checkpackage操作。由于pm传入的callerPackageName等于null
    //如果不是shell 或者没有root权限，均会在这里报SecurityException
    if ((callingUid != Process.SHELL_UID) && (callingUid != Process.ROOT_UID)) {
        mAppOps.checkPackage(callingUid, callerPackageName);
    }
                ......
}
```
从上述Framework中对应的实现来看，如果calling App没有root权限，这样就会在
mAppOps.checkPackage出现SecurityException异常导致静默卸载失败，但是目前Android M的手机root还是比较麻烦的。需要root权限，并给应用赋予root权限。那就再看看看第二种方案。

## 调用PackageInstaller接口实现静默安装
简单的demo实现的code如下
```java
String appPackage = "被卸载应用的包名";
Intent intent = new Intent(mContext, mContext.getClass());
PendingIntent sender = PendingIntent.getActivity(mContext, 0, intent, 0);
PackageInstaller mPackageInstaller = mContext.getPackageManager().getPackageInstaller();
mPackageInstaller.uninstall(appPackage, sender.getIntentSender());
```
PackageInstaller.java中uninstall具体实现如下
```java
public void uninstall(@NonNull String packageName ......) {
    try {
        mInstaller.uninstall(packageName, mInstallerPackageName, 0,                 statusReceiver, mUserId);
    } catch (RemoteException e) {
        throw e.rethrowAsRuntimeException();
    }
}
```
实际的实现，还是在PackageInstallerService.java中的uninstall实现的，如下：
```java
public void uninstall(String packageName, String callerPackageName, int flags,
                        ......
    //checkCallingOrSelfPermission检查自己或者其它调用者是否有权限,
    //从前面权限的分析来，需要应用与系统签名一致，或者应用内置在system
    //priv-app目录下，以及通过此类应用自动更新的应用才能获取权限。
    //实际可以查看/data/system/packages.xml文件对应应用权限分配情况。
    if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DELETE_PACKAGES)
            == PackageManager.PERMISSION_GRANTED) {
        // Sweet, call straight through!
        mPm.deletePackage(packageName, adapter.getBinder(), userId, flags);
    } 
                        ......
}
```
如果应用在获取权限这块没问题的话，正常是可以通过如上方式静默卸载应用的。在查看PackageInstallerService.java中的uninstall的具体信息时，发现如下信息：
```java
public void uninstall(String packageName, String callerPackageName, int flags,
                    ......
// Check whether the caller is device owner
DevicePolicyManager dpm = (DevicePolicyManager)                         mContext.getSystemService(
            Context.DEVICE_POLICY_SERVICE);
    boolean isDeviceOwner = (dpm != null) && dpm.isDeviceOwnerApp(callerPackageName);
} else if (isDeviceOwner) {
// Allow the DeviceOwner to silently delete packages
// Need to clear the calling identity to get DELETE_PACKAGESpermission
mPm.deletePackage(packageName, adapter.getBinder(), userId, flags);
                    ......
}
```
从代码注释就可以了解到，在当前应用的签名与系统不一致，并且不是内置在system/priv-app目录下，checkCallingOrSelfPermission检查到当前应用没有获取到DELETE_PACKAGES的权限，允许DeviceOwner静默卸载应用。这样看来，在应用获取不到对应权限的情况下，还有一种方式可以静默卸载应用，下面简要的介绍一下。
## 通过DeviceOwner实现静默安装
Android从5.0版（Lollipop）开始引入了一个新的概念：Device Owner。如何使一个App成为Device Owner，参考https://source.android.com/devices/tech/admin/provision.html有两种方式，这里就不再简述。一旦应用成为Device Owner，就可以在没有用户干扰的情况下，悄悄的安装，更新，卸载程序了。
首先应用安装，在/data/system/packages.xml目录可以发现如下信息：
```java
<package name="包名"codePath="/data/app/包名-1" .....>
<sigs count="1">
    <cert index="11" key="" />
</sigs>
<proper-signing-keyset identifier="26" />
</package>
```
整个应用的信息中，并没有
```java
<item name="android.permission.DELETE_PACKAGES" granted="true" flags="0" />
```
如果直接调用PackageInstaller来实现静默安装的话，
```java
mContext.checkCallingOrSelfPermission(android.Manifest.permission.DELETE_PACKAGES)
```
获取的PackageManager.PERMISSION_DENIED,这样就直接会卸载失败，接下来做如下几步操作。
先新建一个文件名为device_owner.xml的文件，并写入如下信息：
```xml
<?xml version="1.0" encoding="utf-8" standalone="yes" ?>
<device-owner package="your.owner.app.package.id" name="Your app name" />
```
接下来将文件push到/data/system目录下，然后重启设备
```java
adb push device_owner.xml /data/system
adb reboot
```
这样，再执行应用卸载操作，就可以静默卸载应用了。由于此种方法需要把文件push到data/system目录下，也是需要root权限才能操作的，厂商也可以在做系统的时候，将文件device_owner.xml提前内置到系统中，具体操作本篇就不再阐述了。
## 小结
本片只是简要的从应用卸载接口源码的角度探索了静默卸载应用的几种方式，通过查看Android sdk中提供的接口的具体实现思考了实现的可能。目前的几种方式，比较麻烦的是，都需要应用具备相应比较高的权限（root,shell,或者签名与系统一致，或者内置在system/priv-app目录）。在设备可以获取root权限的情况下，使用pm命令方式或者PackageInstaller均可以静默卸载。在设备无法获取root，而且此应用是设备的内置应用，则可以通过更新应用，添加卸载逻辑代码，通过PackageInstaller是可以静默卸载的。本篇参考Android L以上的代码，可能低于L的平台还有差异性，请区别分析。

# Android M静默卸载
------
一般情况 下，Android系统安装apk会出现一个安装界面，用户可以点击确定或者取消来进行apk的安装。但在实际项目中，有一种需求，就是希望apk在后台安装（不出现安装界面的提示），这种安装方式称为静默安装，同样，apk在后台卸载（不出现卸载界面的提示），这种卸载方式称为静默卸载。本篇暂只讨论与静默卸载相关的情况。

---
## Android平台应用卸载接口
------
卸载应用，pm中PackageInstallerService会响应到。
```java
    frameworks\base\services\core\java\com\android\server\pm\PackageInstallerService.java
    public void uninstall(String packageName, String callerPackageName, int flags,
                IntentSender statusReceiver, int userId) 
```
---
可以发现有两处调用
### 1. 
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

### 2.
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
现在具体查看一下Pm.java中rununinstall的实现
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
mAppOps.checkPackage出现SecurityException异常导致静默卸载失败，但是目前Android M的手机root还是比较麻烦的，而且就算root了，calling App也不一定能够获取到root权限。那就再看看看第二种方案。

## 调用PackageInstaller接口实现静默安装
简单的demo实现的code如下
```java
String appPackage = "被卸载应用的包名";
        Intent intent = new Intent(mContext, mContext.getClass());
        PendingIntent sender = PendingIntent.getActivity(mContext, 0, intent, 0);
        PackageInstaller mPackageInstaller = mContext.getPackageManager().getPackageInstaller();
        mPackageInstaller.uninstall(appPackage, sender.getIntentSender());
```







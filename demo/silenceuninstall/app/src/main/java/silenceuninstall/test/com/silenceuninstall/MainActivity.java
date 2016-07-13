package silenceuninstall.test.com.silenceuninstall;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import silenceuninstall.test.com.runtime.RunScript;

public class MainActivity extends AppCompatActivity {
    private Button slienceUninstall;
    private Button packageInstall;
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mContext = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        slienceUninstall = (Button)findViewById(R.id.SilenceUninstall);
        slienceUninstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String result = RunScript.runIt("pm uninstall -k uninstalltest.test.com.uninstalltest");
                Toast.makeText(mContext,result,Toast.LENGTH_LONG);
            }
        });


        packageInstall = (Button)findViewById(R.id.packageInstall);
        packageInstall.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                runUninstall();
            }
        });
    }

    private void runUninstall()
    {
        String appPackage = "uninstalltest.test.com.uninstalltest";
        Intent intent = new Intent(mContext, mContext.getClass());
        PendingIntent sender = PendingIntent.getActivity(mContext, 0, intent, 0);
        PackageInstaller mPackageInstaller = mContext.getPackageManager().getPackageInstaller();
        mPackageInstaller.uninstall(appPackage, sender.getIntentSender());
    }

}

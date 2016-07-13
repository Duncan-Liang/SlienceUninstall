package silenceuninstall.test.com.silenceuninstall;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import silenceuninstall.test.com.runtime.RunScript;

public class MainActivity extends AppCompatActivity {
    private Button slienceUninstall;
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
                String result = RunScript.runIt("pm uninstall -k com.ss.android.article.news");
                Toast.makeText(mContext,result,Toast.LENGTH_LONG);
            }
        });
    }
}

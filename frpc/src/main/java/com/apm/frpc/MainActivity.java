package com.apm.frpc;

import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.apm.test.R;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {
    String TAG = "FRPC";
    String TAGTOP = "=========================FRPC==============================";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    Intent newIntent
//                            //1.如果自启动APP，参数为需要自动启动的应用包名
//                            = MainActivity.this.getPackageManager().getLaunchIntentForPackage("com.baidu.idl.face.demo");
//                    if (newIntent!=null) {
//                        newIntent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
//                        startActivity(newIntent);
//                    }
//                    Log.d(TAG, TAGTOP);
//                    Log.d(TAG, "start frpc service");
//                    startService(new Intent(MainActivity.this,FrpcService.class));
//                    Log.d(TAG, "start frpc finish");
//                } catch (Exception e) {
//                    Log.d(TAG, TAGTOP);
//                    e.printStackTrace();
//                }
//
//                try {
//                    Intent newIntent
//                            //1.如果自启动APP，参数为需要自动启动的应用包名
//                            = MainActivity.this.getPackageManager().getLaunchIntentForPackage("com.apm.govrs485");
//                    if (newIntent!=null) {
//                        newIntent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
//                        startActivity(newIntent);
//                    }
//                    Log.d(TAG, TAGTOP);
//                    Log.d(TAG, "start frpc service");
//                    startService(new Intent(MainActivity.this,FrpcService.class));
//                    Log.d(TAG, "start frpc finish");
//                } catch (Exception e) {
//                    Log.d(TAG, TAGTOP);
//                    e.printStackTrace();
//                }
//                //frp();
//            }
//        }).start();
        startService(new Intent(this,FrpcService.class));
    }

    private void frp() {
        //PropertiesUtils.Companion.getInstance(this).init();
        StringBuilder builder = new StringBuilder("=============SHELL RESULT=============\r\n");
        //PropertiesUtils instance = PropertiesUtils.Companion.getInstance(MainActivity.this);
        //instance.open();
        //String frpDir = instance.readString("frp-dir", "ls");
        //String frpEnvp = instance.readString("frp-envp", "ls");
        //String frpCmd = instance.readString("frp", "ls");
        //System.out.println("frpCmd = " + frpCmd);
        try {

            Runtime.getRuntime().exec("ls");
            Process process = Runtime.getRuntime()
                    .exec("/data/local/tmp/frp_0.27.0_linux_arm/frpc -c /data/local/tmp/frp_0.27.0_linux_arm/frpc.ini");
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String readLine = errorReader.readLine();
            while (readLine != null) {
                builder.append(readLine);
                builder.append("\r\n");
                readLine = errorReader.readLine();
            }

            BufferedReader dataReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            readLine = dataReader.readLine();
            while (readLine != null) {
                builder.append(readLine);
                builder.append("\r\n");
                readLine = dataReader.readLine();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.e("SHELL", builder.toString());
    }
}

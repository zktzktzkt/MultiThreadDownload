package com.zkt.myapplication;

import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

/**
 * 下载就是把服务器的数据流写入到本地的文件中
 * 对于文件内容的操作用RandomAccessFile
 * <p/>
 * 1.请求服务器
 * 2.new一个File先占位，把file传进RandomAccessFile，让File的文件大小和输入流的length一样
 * 3.计算每个线程所占的大小 总大小/线程数
 * 4.循环计算每个线程的开始位置和结束位置
 * 5.在每个子线程中，请求服务器，设置读取的区间，获取输入流
 * 6.使用RandomAccessFile对象设置在文件的哪个位置写
 * 7.读inputStream，把输入流的东西都写到占位的那个文件中
 */
public class MainActivity extends AppCompatActivity {

    String path = "http://efwefwefw/qwe.exe";
    int threadCount = 3;
    int length;
    int threadFile = 0;
    private Button btnDownload;
    private ProgressBar progressBar;
    int currentProgress;
    private Display defaultDisplay;
    private DisplayMetrics metrics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

      
        progressBar = (ProgressBar) findViewById(R.id.pb_progress);
        btnDownload = (Button) findViewById(R.id.btn_download);

        btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                try {
                    URL url = new URL(path);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    if (conn.getResponseCode() == 200) {
                        length = conn.getContentLength();
                    }
                    progressBar.setMax(length);

                    //创建临时文件
                    File file = new File(Environment.getExternalStorageDirectory(), getNameFromPath(path));
                    RandomAccessFile randomFile = new RandomAccessFile(file, "rwd");
                    randomFile.setLength(length);
                    randomFile.close();

                    //计算每个线程的大小
                    int singleSize = length / threadCount;
                    //计算每个线程下载的开始位置和结束位置
                    for (int id = 0; id < threadCount; id++) {
                        int startIndex = id * singleSize;
                        int endIndex = (id + 1) * singleSize - 1;
                        if (id == threadCount - 1) {
                            endIndex = length - 1;
                        }

                        new DownloadThread(id, startIndex, endIndex).start();
                    }


                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });


    }

    /**
     * 执行下载的线程
     */
    public class DownloadThread extends Thread {
        int startIndex;
        int endIndex;
        int threadId;
        int preProgress = 0;

        public DownloadThread(int threadId, int startIndex, int endIndex) {
            this.threadId = threadId;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        @Override
        public void run() {

            try {
                File fileProgress = new File(Environment.getExternalStorageDirectory(), threadId + ".txt");
                if (fileProgress.exists()) {
                    FileInputStream is = new FileInputStream(fileProgress);
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    preProgress = Integer.parseInt(br.readLine());
                    startIndex += preProgress;
                    is.close();

                    //把上一次的位置添加到进度条当中
                    currentProgress += preProgress;
                    progressBar.setProgress(currentProgress);
                }

                URL url = new URL(path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                //设置请求的区间
                conn.setRequestProperty("Range", "bytes=" + startIndex + "-" + endIndex);

                //请求部分数据，成功的响应吗是206
                if (conn.getResponseCode() == 206) {
                    InputStream is = conn.getInputStream();
                    byte[] b = new byte[1024];
                    int len = 0;
                    File file = new File(Environment.getExternalStorageDirectory(), getNameFromPath(path));
                    RandomAccessFile raf = new RandomAccessFile(file, "rwd");

                    raf.seek(startIndex);   //设定从文件的那个位置开始写

                    int total = preProgress; //记录本线程下载的总进度。把文件中的之前的进度赋给它

                    while ((len = is.read(b)) != -1) {  //下载-------------
                        raf.write(b, 0, len);

                        //创建一个临时进度的文本文件 用来保存写进去的字节数
                        total += len;  //在之前的进度上往后追加读取的字节数
                        RandomAccessFile rafProgress = new RandomAccessFile(fileProgress, "rwd");
                        rafProgress.write(("" + total).getBytes());
                        rafProgress.close();

                        currentProgress += total;
                        progressBar.setProgress(currentProgress);
                    }
                    raf.close();
                    //三条线程全部下载完毕再去删掉所有临时进度
                    threadFile++;
                    synchronized (MainActivity.class) {
                        if (threadFile == threadCount) {
                            for (int i = 0; i < threadCount - 1; i++) {
                                File f = new File(Environment.getExternalStorageDirectory(), i + ".txt");
                                f.delete();
                            }
                            threadFile = 0;
                        }
                    }

                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * 获取文件名
     */
    private String getNameFromPath(String path) {
        int index = path.lastIndexOf("/");
        String fileName = path.substring(index + 1);
        return fileName;
    }
}

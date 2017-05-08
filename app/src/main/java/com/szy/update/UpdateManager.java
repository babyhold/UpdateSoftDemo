package com.szy.update;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

/**
 *@author coolszy
 *@date 2012-4-26
 *@blog http://blog.92coding.com
 */

public class UpdateManager
{
	/* 下载中 */
	private static final int DOWNLOAD = 1;
	/* 下载结束 */
	private static final int DOWNLOAD_FINISH = 2;
	/* 安装apk */
	private static final int DOWNLOAD_AFTER = 3;
	/* 保存解析的XML信息 */
	HashMap<String, String> mHashMap;
	/* 下载保存路径 */
	private String mSavePath;
	/* 记录进度条数量 */
	private int progress;
	/* 是否取消更新 */
	private boolean cancelUpdate = false;

	private Date startDate,endDate;

	private int threadnum = 3;

	private Context mContext;
	/* 更新进度条 */
	private int length;
	private ProgressBar mProgress;
	private TextView mTextProgress;
	private TextView mTextProgressNum;
	private TextView mTextTime;
	private Dialog mDownloadDialog;

	private Handler mHandler = new Handler()
	{
		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
			// 正在下载
			case DOWNLOAD:
				// 设置进度条位置
				mProgress.setProgress(progress);
				mTextProgressNum.setText("线程数："+String.valueOf(threadnum)+"       文件大小："+ String.valueOf(length)+"Byte");


				endDate = new   Date(System.currentTimeMillis());
				long diff = endDate.getTime() - startDate.getTime();

				mTextTime.setText("下载时间："+String.valueOf(diff)+" ms");

				mTextProgress.setText("下载进度: "+String.valueOf(progress)+"%"+"   已下载："+ String.valueOf(length/100000*progress)+"KB"+"   Bps："+ String.valueOf(length/100*progress/diff)+"KBps");
				break;
			case DOWNLOAD_FINISH:
//				endDate = new   Date(System.currentTimeMillis());
//				long diff = endDate.getTime() - startDate.getTime();

//				mTextProgress.setText("下载时间："+String.valueOf(diff)+"ms");

				break;
			case DOWNLOAD_AFTER:
				// 安装文件
				installApk();
				break;
			default:
				break;
			}
		};
	};

	public UpdateManager(Context context)
	{
		this.mContext = context;
	}

	/**
	 * 检测软件更新
	 */
	public void checkUpdate()
	{
		if (isUpdate())
		{
			// 显示提示对话框
			showNoticeDialog();
		} else
		{
			Toast.makeText(mContext, R.string.soft_update_no, Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * 检查软件是否有更新版本
	 * 
	 * @return
	 */
	private boolean isUpdate()
	{
		InputStream inStream = null;
		// 获取当前软件版本
		int versionCode = getVersionCode(mContext);
		// 把version.xml放到网络上，然后获取文件信息

        if(false)
		{
		//从本地文件resources/version.xml 中读取
		 inStream = ParseXmlService.class.getClassLoader().getResourceAsStream("version.xml");}

////////////////////////////////////////////////////////////
		//通过服务器获得版本号
		else {
			String path = "http://192.168.1.2/Android/version.xml";
			try {
				URL url = new URL(path);


				HttpURLConnection conn = (HttpURLConnection) url.openConnection();

				conn.setConnectTimeout(5000);

				conn.setRequestMethod("GET");


				inStream = conn.getInputStream();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

////////////////////////////////////////////////////////////////////////////////////////////////
		// 解析XML文件。 由于XML文件比较小，因此使用DOM方式进行解析
		ParseXmlService service = new ParseXmlService();
		try
		{
			mHashMap = service.parseXml(inStream);
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		if (null != mHashMap)
		{
			int serviceCode = Integer.valueOf(mHashMap.get("version"));
			// 版本判断
			if (serviceCode > versionCode)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * 获取软件版本号
	 * 
	 * @param context
	 * @return
	 */
	private int getVersionCode(Context context)
	{
		int versionCode = 0;
		try
		{
			// 获取软件版本号，对应AndroidManifest.xml下android:versionCode
			versionCode = context.getPackageManager().getPackageInfo("com.szy.update", 0).versionCode;
		} catch (NameNotFoundException e)
		{
			e.printStackTrace();
		}
		return versionCode;
	}

	/**
	 * 显示软件更新对话框
	 */
	private void showNoticeDialog()
	{
		// 构造对话框
		AlertDialog.Builder builder = new Builder(mContext);
		builder.setTitle(R.string.soft_update_title);
		builder.setMessage(R.string.soft_update_info);
		// 更新

		builder.setNegativeButton(R.string.soft_update_updatebtn, new OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				dialog.dismiss();
				// 显示下载对话框
				showDownloadDialog();
			}
		});
		// 稍后更新
		builder.setPositiveButton(R.string.soft_update_later, new OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				dialog.dismiss();
			}
		});
		Dialog noticeDialog = builder.create();
		noticeDialog.show();
	}

	/**
	 * 显示软件下载对话框
	 */
	private void showDownloadDialog()
	{
		// 构造软件下载对话框
		AlertDialog.Builder builder = new Builder(mContext);
		builder.setTitle(R.string.soft_updating);
		// 给下载对话框增加进度条
		final LayoutInflater inflater = LayoutInflater.from(mContext);
		View v = inflater.inflate(R.layout.softupdate_progress, null);
		mProgress = (ProgressBar) v.findViewById(R.id.update_progress);
		mTextProgress =(TextView) v.findViewById(R.id.TextProgress);
		mTextProgressNum =(TextView) v.findViewById(R.id.TextProgressNum);
		mTextTime =(TextView) v.findViewById(R.id.TextTime);

		builder.setView(v);
		// 取消更新
		builder.setNegativeButton(R.string.soft_update_cancel, new OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				dialog.dismiss();
				// 设置取消状态
				cancelUpdate = true;
			}
		});
		mDownloadDialog = builder.create();
		mDownloadDialog.show();
		// 现在文件
		downloadApk();
	}

	/**
	 * 下载apk文件
	 */
	private void downloadApk()
	{
		// 启动新线程下载软件
		new downloadApkThread().start();
	}

	/**
	 * 下载文件线程
	 * 
	 * @author coolszy
	 *@date 2012-4-26
	 *@blog http://blog.92coding.com
	 */
	private class downloadApkThread extends Thread
	{
		@Override
		public void run()
		{
			try
			{
				// 判断SD卡是否存在，并且是否具有读写权限
				if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
					// 获得存储卡的路径
					String sdpath = Environment.getExternalStorageDirectory() + "/";
					mSavePath = sdpath + "download";
					URL url = new URL(mHashMap.get("url"));
					// 创建连接
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					conn.connect();
					// 获取文件大小
					length = conn.getContentLength();
					// 创建输入流
					InputStream is = conn.getInputStream();

					File file = new File(mSavePath);
					// 判断文件目录是否存在
					if (!file.exists()) {
						file.mkdir();
					}
					File apkFile = new File(mSavePath, mHashMap.get("name"));
//					FileOutputStream fos = new FileOutputStream(apkFile);
					RandomAccessFile fos = new RandomAccessFile(apkFile, "rwd");//生成本地文件
					fos.setLength(length);
					fos.close();

					//获取线程数
					threadnum = Integer.parseInt(mHashMap.get("thread"));

					//（mHashMap.get("thread")）;
					//开始计时
					startDate = new Date(System.currentTimeMillis());
					//计算每条线程负责下载的数据量
					int block = length % threadnum == 0 ? length / threadnum : length / threadnum + 1;

//					FileDownloadThread[] threads = new FileDownloadThread[threadnum];
					//	File[] PartFiles = new File[threadnum];
					//String[] PartFiles new String[threadnum];
					ArrayList<String> Partlist = new ArrayList<String>();


					FilePartDownloadThread[] threads = new FilePartDownloadThread[threadnum];
					for (int threadid = 0; threadid < threadnum; threadid++) {
						// 启动线程，分别下载每个线程需要下载的部分
						threads[threadid] = new FilePartDownloadThread(url, apkFile, block,
								(threadid + 1));
						threads[threadid].setName("mydown" + threadid);
						threads[threadid].start();
						Partlist.add(apkFile.getPath() + ".part" + String.valueOf(threadid + 1));
					}

					boolean isfinished = false;
					int downloadedAllSize = 0;
					while ((!isfinished) && (!cancelUpdate))
					// 点击取消就停止下载//.或者下载完成
					{
						isfinished = true;
						// 当前所有线程下载总量
						downloadedAllSize = 0;
						for (int i = 0; i < threadnum; i++) {
							downloadedAllSize += threads[i].getDownloadLength();
							if (!threads[i].isCompleted()) {
								isfinished = false;
							}
						}
						// 通知handler去更新视图组件

						// 计算进度条位置
						progress = (int) (((float) downloadedAllSize / length) * 100);
						// 更新进度
						mHandler.sendEmptyMessage(DOWNLOAD);
						try {
							Thread.sleep(1000);// 休息1秒后再读取下载进度
						} catch (InterruptedException e) {
							e.printStackTrace();
						}

					}
					if(cancelUpdate)
					{
						for (int threadid = 0; threadid < threadnum; threadid++) {
							// 启动线程，分别下载每个线程需要下载的部分
							threads[threadid].interrupt();
						}
				}
						if (downloadedAllSize == length) {
							//合并文件//并删除part文件
//							mergeFiles(apkFile.getPath(), new String[]{apkFile.getPath()+".part1", apkFile.getPath()+".part2",apkFile.getPath()+".part3",apkFile.getPath()+".part4"});
							mergeFiles(apkFile.getPath(), Partlist.toArray(new String[Partlist.size()]));




							// 下载完成
							mHandler.sendEmptyMessage(DOWNLOAD_FINISH);
							try {
								Thread.sleep(1000);// 休息1秒后再读取下载进度
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							// 安装APK
							mHandler.sendEmptyMessage(DOWNLOAD_AFTER);

						}

//					Log.d(TAG, " all of downloadSize:" + downloadedAllSize);


				}


			} catch (IOException e)
			{
				e.printStackTrace();
			}
			// 取消下载对话框显示
			mDownloadDialog.dismiss();
		}
	};

	//负责下载操作
	private final class DownloadThread extends Thread{
		private int threadid;
		private URL downpath;
		private int block;
		private File file;

		public DownloadThread(int threadid, URL downpath, int block, File file) {
			this.threadid = threadid;
			this.downpath = downpath;
			this.block = block;
			this.file = file;
		}
		public void run() {
			int startposition = threadid * block;//从网络文件的什么位置开始下载数据
			int endposition = (threadid+1) * block - 1;//下载到网络文件的什么位置结束
			//指示该线程要从网络文件的startposition位置开始下载，下载到endposition位置结束
			//Range:bytes=startposition-endposition
			try{
				RandomAccessFile accessFile = new RandomAccessFile(file, "rwd");
				accessFile.seek(startposition);//移动指针到文件的某个位置
				HttpURLConnection conn = (HttpURLConnection) downpath.openConnection();
				conn.setConnectTimeout(5000);
				conn.setRequestMethod("GET");
				conn.setRequestProperty("Range", "bytes="+ startposition+ "-"+ endposition);
				InputStream inStream = conn.getInputStream();
				byte[] buffer = new byte[1024];
				int len = 0;
				while( (len = inStream.read(buffer)) != -1 ){
					accessFile.write(buffer, 0, len);
					System.out.println("第"+ (threadid+1)+ "下载"+len);
				}
				accessFile.close();
				inStream.close();
				System.out.println("第"+ (threadid+1)+ "线程下载完成");
				endDate = new   Date(System.currentTimeMillis());
				long diff = endDate.getTime() - startDate.getTime();

				//然后在文本框中显示出来：
				System.out.println("第"+ (threadid+1)+"线程完成time:"+diff);

			}catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 安装APK文件
	 */
	private void installApk()
	{
		File apkfile = new File(mSavePath, mHashMap.get("name"));
		if (!apkfile.exists())
		{
			return;
		}
		// 通过Intent安装APK文件
		Intent i = new Intent(Intent.ACTION_VIEW);
		i.setDataAndType(Uri.parse("file://" + apkfile.toString()), "application/vnd.android.package-archive");
		mContext.startActivity(i);
	}



		public static void mergeFiles(String outFile, String[] files) {
			int BUFSIZE = 1024 * 8;
			FileChannel outChannel = null;
			if(files.length==1)
			{
				File oldfile=new File(files[0]);
				File newfile=new File(outFile);
				deleteFile(outFile);
				oldfile.renameTo(newfile);
			}
			else {
				//out.println("Merge " + Arrays.toString(files) + " into " + outFile);
				try {
					outChannel = new FileOutputStream(outFile).getChannel();
					for (String f : files) {
						FileChannel fc = new FileInputStream(f).getChannel();
						ByteBuffer bb = ByteBuffer.allocate(BUFSIZE);
						while (fc.read(bb) != -1) {
							bb.flip();
							outChannel.write(bb);
							bb.clear();
						}
						fc.close();
						deleteFile(f);
					}
					//	out.println("Merged!! ");
				} catch (IOException ioe) {
					ioe.printStackTrace();
				} finally {
					try {
						if (outChannel != null) {
							outChannel.close();
						}
					} catch (IOException ignore) {
					}
				}
			}
		}

	private static boolean deleteFile(String fileName) {
		File file = new File(fileName);
		// 如果文件路径所对应的文件存在，并且是一个文件，则直接删除
		if (file.exists() && file.isFile()) {
			if (file.delete()) {
				System.out.println("删除单个文件" + fileName + "成功！");
				return true;
			} else {
				System.out.println("删除单个文件" + fileName + "失败！");
				return false;
			}
		} else {
			System.out.println("删除单个文件失败：" + fileName + "不存在！");
			return false;
		}
	}


//		public static void main(String[] args) {
//			mergeFiles("D:/output.txt", new String[]{"D:/in_1.txt", "D:/in_2.txt", "D:/in_3.txt"});
//		}
	}

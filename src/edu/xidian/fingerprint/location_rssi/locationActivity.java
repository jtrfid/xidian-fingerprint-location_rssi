package edu.xidian.fingerprint.location_rssi;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.logging.LogManager;
import org.altbeacon.beacon.logging.Loggers;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import edu.xidian.FindBeacons.FindBeacons;
import edu.xidian.FindBeacons.FindBeacons.OnBeaconsListener;
import edu.xidian.FindBeacons.RssiDbManager;
import edu.xidian.FindBeacons.RssiInfo;
import edu.xidian.logtofile.LogcatHelper;

/**
 * 指纹定位与rssi距离定位比较
 * 指纹算法第二阶段，即，在线定位阶段。在线测得各个beacon的rssi值，与第一阶段存入数据库的各个定位参考点的各beacons的rssi值比对，获得最近的定位参考点。
 * 数据库管理：edu.xidian.FindBeacons.RssiDbManager，数据库文件：sdcard/rssiRecord/rssi.db
 */
public class locationActivity extends Activity {
	private final static String TAG = locationActivity.class.getSimpleName();
    private FindBeacons mFindBeacons;
    
    // 每次扫描周期结束，执行此回调，获取附近beacons信息
    private OnBeaconsListener mBeaconsListener = new OnBeaconsListener() {
		@Override
		public void getBeacons(Collection<Beacon> beacons) {		
			// 日志记录和屏幕显示Beacon信息
			// 有可能到达采样周期时，没有找到所有beacons，甚至是0个beacons，因此，应重复记录，一直到采样周期结束。当然是最后更新的有效。
			String str = "beacons=" + beacons.size();
			LogManager.d(TAG,str);
			logToDisplay(str);
			
			if(beacons.size() == 0) return;
			
			/** 根据指纹定位算法定位  */
		    fingerprint(beacons);
		    /** 根据rssi距离模型定位  */
		    rssi_distance(beacons);
		}
    	
    }; 
    
    /** 根据指纹定位算法定位  */
    private void fingerprint(Collection<Beacon> beacons)
    {
		String str;
    	// 定位参考点名称
		String RPname;
		/**
		 * key: beacon的major,minor组成的字符串major_minor;
		 * value: rssi平均值
		 */
		Map<String,Double> RSSIs = new HashMap<String,Double>();
		
		Double diff_sum = 0.0;
		Double min_value = 0.0;
		String min_RPname = null;
		for(RssiInfo rssi_info : mRssiInfo) { // RP,遍历每个定位参考点
			diff_sum = 0.0; 
			RPname = rssi_info.getRPname();
			RSSIs = rssi_info.getRSSIs();
			for (Beacon beacon : beacons) { // 遍历本次扫描周期测到的各个beacon
				// becaon的两个id(major,minor)，rssi及其平均值
				String key = beacon.getId2()+"_"+beacon.getId3();
				Double rssi = beacon.getRunningAverageRssi();
				Double rssi_db = RSSIs.get(key);
				//logToDisplay("key="+key+",rssi="+rssi+",rssi_db="+rssi_db);
                if (rssi_db != null) 
					diff_sum = diff_sum + (rssi-rssi_db)*(rssi-rssi_db)/beacons.size();
				else {
					str = "数据库中无key="+key;
					LogManager.d(TAG, str);
					logToDisplay(str);
				}
			}  // RSSIs
			if (min_value == 0.0) {
				min_value = diff_sum;
				min_RPname = RPname;
			}
			else if(min_value > diff_sum) {
				min_value = diff_sum;
				min_RPname = RPname;
			}
			//str = "参考点["+ RPname + "]rssi单位平方差="+ String.format("%.2f",diff_sum);
			//LogManager.d(TAG, str);
		    //logToDisplay(str);    
		} // for RP
		str = "最近参考点："+min_RPname;
		LogManager.d(TAG, str);
	    logToDisplay(str);
    }
    
    /** 根据rssi距离模型定位  */
    private void rssi_distance(Collection<Beacon> beacons)
    {
		String str;  	
		double min_distance = 100.0; // 最近beacon的距离
		double d;
		String min_Ids = null; // 最近becaon的两个id(major,minor)组成的字符串
		for (Beacon beacon : beacons) { // 遍历本次扫描周期测到的各个beacon
			d = beacon.getDistance();
			if (d < min_distance) { 
			   min_Ids = beacon.getId2()+"_"+beacon.getId3();
			   min_distance = d;
			}
			//str = "beacon["+ beacon.getId2()+"_"+beacon.getId3() + "]距离="+ String.format("%.2f",d);
			//LogManager.d(TAG, str);
		    //logToDisplay(str);    
		} 
		str = "最近beacon："+min_Ids+","+String.format("%.2f",min_distance);
		LogManager.d(TAG, str);
	    logToDisplay(str);
    }
       
    private static LogcatHelper loghelper;  //日志文件
    private Button start_logfile; // 开始记录日志文件
    private Button end_logfile;   // 停止日志文件
    private String Logformat = "";  // 日志拟制符格式
    
    private Button mStart_btn;  // 开始监控(查找)beacons
    private Button mStop_btn;   // 停止监控(查找)beacons
    
    private EditText ScanPeriod_edit;  // 前台扫描周期，缺省1.1s
    
    /** rssi采样周期,即，计算该时间段内的平均RSSI（首末各去掉10%）,缺省是20秒(20000毫秒) */
    private EditText SamplePeriod_edit; // s
        
    /** 数据库管理 */
    private RssiDbManager mRssiDbManager;
    
    /** 数据库中存储的各个定位参考点的beacons的rssi平均值 */
    List<RssiInfo> mRssiInfo;
    
    /** 写标记到日志 */
    private EditText mark_edit;
        
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// 建议使用org.altbeacon.beacon.logging.LogManager.javaLogManager输出日志，altbeacon就是使用这种机制，便于发布版本时，减少输出日志信息。
		// 输出所有ERROR(Log.e()), WARN(Log.w()), INFO(Log.i()), DEBUG(Log.d()), VERBOSE(Log.v())
		// 对应日志级别由高到低
        LogManager.setLogger(Loggers.verboseLogger());
		
        // 全部不输出，在release版本中设置
        //LogManager.setLogger(Loggers.empty());
		
        // 输出ERROR(Log.e()), WARN(Log.w()),缺省状态，仅输出错误和警告信息，即输出警告级别以上的日志
        //LogManager.setLogger(Loggers.warningLogger());
        
        // 试验日志输出
//        LogManager.e(TAG,"Error");
//        LogManager.w(TAG,"Warn");
//        LogManager.i(TAG,"info");
//        LogManager.d(TAG,"debug");
//        LogManager.v(TAG,"verbose");

		LogManager.d(TAG, "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// 日志文件
		start_logfile = (Button)findViewById(R.id.start_log);
		end_logfile = (Button)findViewById(R.id.end_log);
		
		// 设置SD卡中的日志文件,sd卡根目录/rssiRecord/mydistance.log
		loghelper = LogcatHelper.getInstance(this,"rssiRecord","mydistance.log");
		// 设置SD卡中的日志文件,sd卡根目录/mydistance.log
		//loghelper = LogcatHelper.getInstance(this,"","mydistance.log");
		
		// 打印D级以上(包括D,I,W,E,F)的TAG，其它tag不打印
		//Logformat = TAG + ":D *:S";
		
		// 打印D级以上的TAG，和LogcatHelper全部，其它tag不打印
		//Logformat = TAG + ":D LogcatHelper:V *:S";
		
		// 打印D以上的TAG和RunningAverageRssiFilter，其他tag不打印(*:S)
		Logformat = TAG + ":D RunningAverageRssiFilter:D *:S";
		
		// 打印D以上的FindBeacons，其他tag不打印(*:S)
		// Logformat = "FindBeacons:D *:S";
		
		//Logformat = "RangedBeacon:V *:S";
		
		// 打印所有日志， priority=V | D | I | W | E ,级别由低到高
		// Logformat = "";
		
		// 日志文件
		loghelper.start(Logformat);  
		
		// "开始记录日志"按钮失效,此时已经开始记录日志
		start_logfile.setEnabled(false);
		
		// 开始/停止监控（查找）beacons
		mStart_btn = (Button)findViewById(R.id.Mstart);
		mStop_btn = (Button)findViewById(R.id.Mstop);
		mStop_btn.setEnabled(false);
				
		// 获取FindBeacons唯一实例
		mFindBeacons = FindBeacons.getInstance(this);
                
    	// 设置默认前台扫描周期,default 1.1s
		ScanPeriod_edit = (EditText)findViewById(R.id.ScanPeriod_edit);
        ScanPeriod_edit.setText("1.1");
        onForegroundScanPeriod(null);
        
        // rssi采样周期,即，计算该时间段内的平均RSSI（首末各去掉10%）,缺省是20秒(20000毫秒)
        SamplePeriod_edit = (EditText)findViewById(R.id.SamplePeriod_edit);
        SamplePeriod_edit.setText("10");  // 10s
        onSamplePeriod(null);
        
        /** 写标记到日志 */
        mark_edit = (EditText)findViewById(R.id.Mark_edt);
                
        // 数据库管理, 获取数据库中存储的各个定位参考点的beacons的rssi平均值
        mRssiDbManager = new RssiDbManager(locationActivity.this);
        mRssiInfo = mRssiDbManager.getRssiInfo();
        
        // 设置获取附近所有beacons的监听对象，在每个扫描周期结束，通过该接口获取找到的所有beacons
        mFindBeacons.setBeaconsListener(mBeaconsListener);
             
    	// 查看手机蓝牙是否可用,若当前状态为不可用，则默认调用意图请求打开系统蓝牙
    	mFindBeacons.checkBLEEnable();

        logToDisplay("Mstart,Mstop分别代表查找beacon的开始和结束");
	}
	
    @Override 
    protected void onDestroy() {
    	LogManager.d(TAG,"onDestroy()");
        super.onDestroy();
        
        mFindBeacons.closeSearcher(); 
        loghelper.stop();
    }
    
    /** 
     * 连续两次按回退键，退出程序。
     * 使得下次程序执行从onCreate()开始，避免数据库初始化等问题的出现。
     */
    private long mPressedTime = 0;
    @Override
	public void onBackPressed() {
    	long mNowTime = System.currentTimeMillis();//获取第一次按键时间
    	if((mNowTime - mPressedTime) > 2000){//比较两次按键时间差
    	Toast.makeText(this, "再按一次退出程序", Toast.LENGTH_SHORT).show();
    	mPressedTime = mNowTime;
    	}
    	else{//退出程序
    	   this.finish();
    	   System.exit(0);
    	}

		// super.onBackPressed();
	}
    
    /** 开始记录日志文件 */
    public void onStartLog(View view) {
    	loghelper.start(Logformat);  
    	start_logfile.setEnabled(false);
    	end_logfile.setEnabled(true);
    }
    
    /** 结束记录日志文件 */
    public void onEndLog(View view) {
    	loghelper.stop();
    	start_logfile.setEnabled(true);
    	end_logfile.setEnabled(false);
    }
    
    /** 删除日志文件文件 */
    public void onDelLog(View view) {
    	// loghelper.delLogDir();  // 删除日志目录和文件，如果日志文件和数据库文件放在一个目录下，不要用此函数。
    	loghelper.delLogfile();
    }
       
    /** 开始查找附近beacons */
    public void onMonitoringStart(View view) {
    	logToDisplay("onMonitoringStart(),startMonitoringBeaconsInRegion");
    	LogManager.d(TAG,"onMonitoringStart(),startMonitoringBeaconsInRegion");
    	
    	// 根据编辑框，设置前台扫描周期,default 1.1s
    	onForegroundScanPeriod(null);
    	        
        // 根据编辑框，设置rssi采样周期,即，计算该时间段内的平均RSSI（首末各去掉10%）,缺省是20秒(20000毫秒)
    	onSamplePeriod(null);
    	
    	mFindBeacons.openSearcher();
    	mStart_btn.setEnabled(false);
    	mStop_btn.setEnabled(true);
    }
    
    /** 停止查找beacons */
    public void onMonitoringStop(View view) {
    	logToDisplay("onMonitoringStop(),stopMonitoringBeaconsInRegion");
    	LogManager.d(TAG,"onMonitoringStop(),stopMonitoringBeaconsInRegion");
    	mFindBeacons.closeSearcher();
    	mStart_btn.setEnabled(true);
    	mStop_btn.setEnabled(false);
    }
    
    /** 设置前台扫描周期 */
    public void onForegroundScanPeriod(View view) {
    	String period_str = ScanPeriod_edit.getText().toString();
        long period = (long)(Double.parseDouble(period_str) * 1000.0D);
        mFindBeacons.setForegroundScanPeriod(period);   
    }
    
    /** 
     * 设置rssi采样周期,即，计算该时间段内的平均RSSI（首末各去掉10%）,缺省是20秒(20000毫秒)
     */
    public void onSamplePeriod(View view) {
    	String period_str = SamplePeriod_edit.getText().toString();
    	int SamplePeroid = (int)(Double.parseDouble(period_str) * 1000.0D);
        FindBeacons.setSampleExpirationMilliseconds(SamplePeroid);   
    }
    
    /** 写标记到日志 */
    public void onMark(View view) {
    	String str = "***" + mark_edit.getText().toString() + "***";
    	LogManager.d(TAG, str);
    	logToDisplay(str);
    }
     
    public void logToDisplay(final String line) {
    	runOnUiThread(new Runnable() {
    		Date date = new Date(System.currentTimeMillis());
    		SimpleDateFormat sfd = new SimpleDateFormat("HH:mm:ss.SSS",Locale.CHINA);
	    	String dateStr = sfd.format(date);
    	    public void run() {
    	    	TextView editText = (TextView)locationActivity.this.findViewById(R.id.monitoringText);
       	    	editText.append(dateStr+"=="+line+"\n"); 
       	    	// 滚动到底部
       	    	ScrollView sv = (ScrollView)locationActivity.this.findViewById(R.id.scrollView);
       	    	sv.scrollTo(0, editText.getBottom());
    	    }
    	});
    }
}

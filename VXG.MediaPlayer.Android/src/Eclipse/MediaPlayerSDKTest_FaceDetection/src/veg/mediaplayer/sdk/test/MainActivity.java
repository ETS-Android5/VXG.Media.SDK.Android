/*
 *
  * Copyright (c) 2011-2017 VXG Inc.
 *
 */


package veg.mediaplayer.sdk.test;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Button;
import android.widget.TextView;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.*;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView.OnEditorActionListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

import android.preference.PreferenceManager;
import veg.mediaplayer.sdk.MediaPlayer;
import veg.mediaplayer.sdk.MediaPlayer.MediaPlayerCallback;
import veg.mediaplayer.sdk.MediaPlayer.PlayerModes;
import veg.mediaplayer.sdk.MediaPlayer.PlayerNotifyCodes;
import veg.mediaplayer.sdk.MediaPlayer.PlayerProperties;
import veg.mediaplayer.sdk.MediaPlayer.PlayerRecordFlags;
import veg.mediaplayer.sdk.MediaPlayer.PlayerState;
import veg.mediaplayer.sdk.MediaPlayer.VideoShot;
import veg.mediaplayer.sdk.MediaPlayerConfig;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.Frame.Builder;


public class MainActivity extends Activity implements OnClickListener, MediaPlayer.MediaPlayerCallback,  MediaPlayer.MediaPlayerCallbackData
{
    private static final String TAG 	 = "MediaPlayerTest";
    
	public  static AutoCompleteTextView	edtIpAddress;
	public  static ArrayAdapter<String> edtIpAddressAdapter;
	public  static Set<String>			edtIpAddressHistory;
	private Button						btnConnect;
	private Button						btnHistory;
	private Button						btnShot;
	private Button						btnRecord;
	private boolean						is_record = false;


	private SharedPreferences 			settings;
    private SharedPreferences.Editor 	editor;

    private boolean 					playing = false;
    private MediaPlayer 				player = null;
    //private MediaPlayer 				player_record = null;
    private MainActivity 				mthis = null;

    private RelativeLayout 				playerStatus = null;
    private TextView 					playerStatusText = null;
    private TextView 					playerHwStatus = null;
    
	public ScaleGestureDetector 		detectors = null;	

    private MulticastLock multicastLock = null;

	private FaceDetector 				faceDetector = null;
    
	private enum PlayerStates
	{
	  	Busy,
	  	ReadyForUse
	};

    private enum PlayerConnectType
	{
	  	Normal,
	  	Reconnecting
	};
    
	private Object waitOnMe = new Object();
	private PlayerStates player_state = PlayerStates.ReadyForUse; 
	private PlayerConnectType reconnect_type = PlayerConnectType.Normal;
	private int mOldMsg = 0;

	private Toast toastShot = null;
	
	// Event handler
	
	private Handler handler = new Handler() 
    {
		String strText = "Connecting";
		
		String sText;
		String sCode;
		
		@Override
	    public void handleMessage(Message msg) 
	    {
	    	PlayerNotifyCodes status = (PlayerNotifyCodes) msg.obj;
	        switch (status) 
	        {
	        	case CP_CONNECT_STARTING:
	        		if (reconnect_type == PlayerConnectType.Reconnecting)
	        			strText = "Reconnecting";
	        		else
	        			strText = "Connecting";
	        			

					player_state = PlayerStates.ReadyForUse;
	    			showStatusView();
	    			
	    			reconnect_type = PlayerConnectType.Normal;
	    			setHideControls();
	    			break;
	    			
	        	case PLP_BUILD_SUCCESSFUL:
					player_state = PlayerStates.ReadyForUse;
	        		sText = player.getPropString(PlayerProperties.PP_PROPERTY_PLP_RESPONSE_TEXT);
	        		sCode = player.getPropString(PlayerProperties.PP_PROPERTY_PLP_RESPONSE_CODE);
	        		Log.i(TAG, "=Status PLP_BUILD_SUCCESSFUL: Response sText="+sText+" sCode="+sCode);
	        		break;
	                
		    	case VRP_NEED_SURFACE:
					player_state = PlayerStates.ReadyForUse;
		    		showVideoView();
					break;
	
		    	case PLP_PLAY_SUCCESSFUL:
		    		player_state = PlayerStates.ReadyForUse;
	    			playerStatusText.setText("");
		    		setTitle(R.string.app_name);
			        break;
	                
	        	case PLP_CLOSE_STARTING:
	        		player_state = PlayerStates.Busy;
	    			playerStatusText.setText("Disconnected");
	    			showStatusView();
	    			setUIDisconnected();
	    			break;
	                
	        	case PLP_CLOSE_SUCCESSFUL:
	        		player_state = PlayerStates.Busy;
	    			playerStatusText.setText("Disconnected");
	    			showStatusView();
	    			System.gc();
	    			setShowControls();
	    			setUIDisconnected();
	                break;
	                
	        	case PLP_CLOSE_FAILED:
	        		player_state = PlayerStates.Busy;
	    			playerStatusText.setText("Disconnected");
	    			showStatusView();
	    			setShowControls();
	    			setUIDisconnected();
	   			break;
	               
	        	case CP_CONNECT_FAILED:
	        		player_state = PlayerStates.Busy;
	    			playerStatusText.setText("Disconnected");
	    			showStatusView();
	    			setShowControls();
	    			setUIDisconnected();
	    			break;
	                
	            case PLP_BUILD_FAILED:
	        		sText = player.getPropString(PlayerProperties.PP_PROPERTY_PLP_RESPONSE_TEXT);
	        		sCode = player.getPropString(PlayerProperties.PP_PROPERTY_PLP_RESPONSE_CODE);
	        		Log.i(TAG, "=Status PLP_BUILD_FAILED: Response sText="+sText+" sCode="+sCode);

	        		player_state = PlayerStates.Busy;
	    			playerStatusText.setText("Disconnected");
	    			showStatusView();
	    			setShowControls();
	    			setUIDisconnected();
	    			break;
	                
	            case PLP_PLAY_FAILED:
	            	player_state = PlayerStates.ReadyForUse;
	    			playerStatusText.setText("Disconnected");
	    			showStatusView();
	    			setShowControls();
	    			setUIDisconnected();
	    			break;
	                
	            case PLP_ERROR:
	            	player_state = PlayerStates.ReadyForUse;
	    			playerStatusText.setText("Disconnected");
	    			showStatusView();
	    			setShowControls();
	    			setUIDisconnected();
	    			break;
	                
	            case CP_INTERRUPTED:
	            	player_state = PlayerStates.ReadyForUse;
	    			playerStatusText.setText("Disconnected");
	    			showStatusView();
	    			setShowControls();
	    			setUIDisconnected();
	    			break;
	            case CP_RECORD_STARTED:
	            	Log.v(TAG, "=handleMessage CP_RECORD_STARTED");
	            	{
	            		String sFile = player.RecordGetFileName(1);
	            		Toast.makeText(getApplicationContext(),"Record Started. File "+sFile, Toast.LENGTH_LONG).show();
	            	}
	            	break;

	            case CP_RECORD_STOPPED:
	            	Log.v(TAG, "=handleMessage CP_RECORD_STOPPED");
	            	{
	            		String sFile = player.RecordGetFileName(0);
	            		Toast.makeText(getApplicationContext(),"Record Stopped. File "+sFile, Toast.LENGTH_LONG).show();
	            	}
	            	break;

	            //case CONTENT_PROVIDER_ERROR_DISCONNECTED:
	            case CP_STOPPED:
	            case VDP_STOPPED:
	            case VRP_STOPPED:
	            case ADP_STOPPED:
	            case ARP_STOPPED:
	            	if (player_state != PlayerStates.Busy)
	            	{
	            		player_state = PlayerStates.Busy;
						if (toastShot != null)
							toastShot.cancel();
	            		player.Close();
	        			playerStatusText.setText("Disconnected");
		    			showStatusView();
		    			player_state = PlayerStates.ReadyForUse;
		    			setShowControls();
		    			setUIDisconnected();
	            	}
	                break;

				case CP_ERROR_NODATA_TIMEOUT:
					Toast.makeText(getApplicationContext(), "CP_ERROR_NODATA_TIMEOUT "+player_state,
							   Toast.LENGTH_SHORT).show();
					break;
					
				case PLP_TRIAL_VERSION:
				case PLP_EOS:
	            case CP_ERROR_DISCONNECTED:
					Toast.makeText(getApplicationContext(), ((status == PlayerNotifyCodes.PLP_EOS) ? "PLP_EOS ":"CP_ERROR_DISCONNECTED ")+player_state,
							   Toast.LENGTH_SHORT).show();
	            	if (player_state != PlayerStates.Busy)
	            	{
	            		player_state = PlayerStates.Busy;
						if (toastShot != null)
							toastShot.cancel();
	            		player.Close();

	        			playerStatusText.setText("Disconnected");
		    			showStatusView();
		    			player_state = PlayerStates.ReadyForUse;
		    			setUIDisconnected();

						if(status == PlayerNotifyCodes.PLP_TRIAL_VERSION){
							Toast.makeText(getApplicationContext(), "Demo Version!",
								   Toast.LENGTH_SHORT).show();
						}
	            	}
	                break;
	        }
	    }
	};

	// callback from Native Player 
	@Override
	public int OnReceiveData(ByteBuffer buffer, int size, long pts) 
	{
		Log.e(TAG, "Form Native Player OnReceiveData: size: " + size + ", pts: " + pts);
		return 0;
	}
    

	// All event are sent to event handlers    
	@Override
	public int Status(int arg)
	{
		
		PlayerNotifyCodes status = PlayerNotifyCodes.forValue(arg);
		if (handler == null || status == null)
			return 0;
		
		Log.e(TAG, "From Native Player status: " + arg);
	    switch (PlayerNotifyCodes.forValue(arg)) 
	    {
	        default:     
				Message msg = new Message();
				msg.obj = status;
				//handler.removeMessages(mOldMsg);
				//mOldMsg = msg.what;
				handler.sendMessage(msg);
	    }
	    
		return 0;
	}
	
    public String getRecordPath()
    {
    	File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
    		      Environment.DIRECTORY_DCIM), "RecordsMediaPlayer");
    	
	    if (! mediaStorageDir.exists()){
	        if (!(mediaStorageDir.mkdirs() || mediaStorageDir.isDirectory())){
	            Log.e(TAG, "<=getRecordPath() failed to create directory path="+mediaStorageDir.getPath());
	            return "";
	        }
	    }
	    return mediaStorageDir.getPath();
    }


	   // Will received for SW decoder only, because for HW we use hardware memory and operate only index
		// For receive video frame for HW decoder we will use GLSurfaceView 
		// videoViewForHW.getFrame as sample
		@Override
		public int OnVideoRendererFrameAvailable(ByteBuffer buffer, int size, String format_fourcc, int width, int height,
				int bytes_per_row, long pts, int will_show) {
				
			Log.e(TAG, "OnVideoRendererFrameAvailable: size:" + size + ", format_fourcc: " + format_fourcc + ", width: " + width 
					+ ", height: " + height + ", bytes_per_row: " + bytes_per_row + ", pts: " + pts + ", will_show: " + will_show);

			int format = ImageFormat.NV21;
			if (format_fourcc == "NV12")
				format = ImageFormat.NV21;

			Log.e(TAG, "OnVideoRendererFrameAvailable: start setImageData");
				
			Frame frame1 = new Frame.Builder().setImageData(buffer, width, height, format).build();
			
			Log.e(TAG, "OnVideoRendererFrameAvailable: Start Detect");
			
			SparseArray<Face> faces = faceDetector.detect(frame1);
			
			Log.e(TAG, "OnVideoRendererFrameAvailable: Detected");
			
			Log.v(TAG, "FACE :" + faces.size());
			for(int i=0; i<faces.size(); i++) {
			  Face thisFace = faces.valueAt(i);
			  float x1 = thisFace.getPosition().x;
			  float y1 = thisFace.getPosition().y;
			  float x2 = x1 + thisFace.getWidth();
			  float y2 = y1 + thisFace.getHeight();
			  Log.v(TAG, "FACE: " + x1 + ":" + y1 + "	   " +	x2 + ":" + y2);
			  //tempCanvas.drawRoundRect(new RectF(x1, y1, x2, y2), 2, 2, myRectPaint);
			}
			//myImageView.setImageDrawable(new BitmapDrawable(getResources(),tempBitmap));
			
	
			return 0;
		}


	
    @Override
    public void onCreate(Bundle savedInstanceState) 
	{
		String  strUrl;

		setTitle(R.string.app_name);
		super.onCreate(savedInstanceState);

		WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		multicastLock = wifi.createMulticastLock("multicastLock");
		multicastLock.setReferenceCounted(true);
		multicastLock.acquire();
		
		getWindow().requestFeature(Window.FEATURE_PROGRESS);
		getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_VISIBILITY_ON);
		
		setContentView(R.layout.main);
		mthis = this;
		
		settings = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

		SharedSettings.getInstance(this).loadPrefSettings();
		SharedSettings.getInstance().savePrefSettings();
		
		playerStatus 		= (RelativeLayout)findViewById(R.id.playerStatus);
		playerStatusText 	= (TextView)findViewById(R.id.playerStatusText);
		playerHwStatus 		= (TextView)findViewById(R.id.playerHwStatus);
		
		player = (MediaPlayer)findViewById(R.id.playerView);
		
		//record only
		//player_record = new MediaPlayer(this, false);

		strUrl = settings.getString("connectionUrl", "rtsp://184.72.239.149/vod/BigBuckBunny_115k.mov");
		
		SurfaceHolder sfhTrackHolder = player.getSurfaceView().getHolder();
		sfhTrackHolder.setFormat(PixelFormat.TRANSPARENT);
		
		HashSet<String> tempHistory = new HashSet<String>();

		tempHistory.add("http://devimages.apple.com/iphone/samples/bipbop/gear1/prog_index.m3u8");
		tempHistory.add("rtsp://184.72.239.149/vod/BigBuckBunny_115k.mov");

        faceDetector = new FaceDetector.Builder(getApplicationContext())
                .setTrackingEnabled(false)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .build();
		
	
		player.setOnTouchListener(new View.OnTouchListener() 
		{
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) 
            {
                switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) 
                {
                    case MotionEvent.ACTION_DOWN:
                    {
                    	if (player.getState() == PlayerState.Paused)
                    		player.Play();
                    	else
                        	if (player.getState() == PlayerState.Started){
                        		player.Pause();
                        		//player.PauseFlush();
                        	}
                    }
                }
            		
	        	return true;
            }
        });
		
			
		edtIpAddressHistory = settings.getStringSet("connectionHistory", tempHistory);

		this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN); 
	
		edtIpAddress = (AutoCompleteTextView)findViewById(R.id.edit_ipaddress);
		edtIpAddress.setText(strUrl);

		edtIpAddress.setOnEditorActionListener(new OnEditorActionListener() 
		{
			@Override
			public boolean onEditorAction(TextView v, int actionId,	KeyEvent event) 
			{
				if (event != null&& (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) 
				{
					InputMethodManager in = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					in.hideSoftInputFromWindow(edtIpAddress.getApplicationWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
					return true;
	
				}
				return false;
			}
		});

		btnHistory = (Button)findViewById(R.id.button_history);

		// Array of choices
		btnHistory.setOnClickListener(new View.OnClickListener() 
		{
			public void onClick(View v) 
			{
				InputMethodManager in = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				in.hideSoftInputFromWindow(MainActivity.edtIpAddress.getApplicationWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
				
				if (edtIpAddressHistory.size() <= 0)
					return;



				MainActivity.edtIpAddressAdapter = new ArrayAdapter<String>(MainActivity.this, R.layout.history_item, new ArrayList<String>(edtIpAddressHistory));
				MainActivity.edtIpAddress.setAdapter(MainActivity.edtIpAddressAdapter);
				MainActivity.edtIpAddress.showDropDown();
			}   
		});

		btnShot = (Button)findViewById(R.id.button_shot);

		// Array of choices
		btnShot.setOnClickListener(new View.OnClickListener() 
		{
			public void onClick(View v) 
			{
				if (player != null)
				{
					Log.e("SDL", "getVideoShot()");

	    	    	SharedSettings sett = SharedSettings.getInstance();
					
					//VideoShot frame = player.getVideoShot(200, 200);
					VideoShot frame = player.getVideoShot(-1, -1);
					if (frame == null)
						return;
					
					// get your custom_toast.xml ayout
					LayoutInflater inflater = getLayoutInflater();
	 
					View layout = inflater.inflate(R.layout.videoshot_view,
					  (ViewGroup) findViewById(R.id.videoshot_toast_layout_id));
	 
					ImageView image = (ImageView) layout.findViewById(R.id.videoshot_image);
					image.setImageBitmap(getFrameAsBitmap(frame.getData(), frame.getWidth(), frame.getHeight()));
					
					// Toast...
					if (toastShot != null)
						toastShot.cancel();

					toastShot = new Toast(getApplicationContext());
					toastShot.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
					toastShot.setDuration(Toast.LENGTH_SHORT);
					toastShot.setView(layout);
					toastShot.show();
					
				}
			}   
		});

		btnConnect = (Button)findViewById(R.id.button_connect);
        btnConnect.setOnClickListener(this);
        
        btnRecord = (Button) findViewById(R.id.button_record);
        //btnRecord.setVisibility(View.GONE);
        btnRecord.setOnClickListener( new OnClickListener(){
			@Override
			public void onClick(View arg0) {
				is_record = !is_record;

				/*if(player_record != null){
					if(is_record){
						//start recording
						int record_flags = PlayerRecordFlags.forType(PlayerRecordFlags.PP_RECORD_AUTO_START) | PlayerRecordFlags.forType(PlayerRecordFlags.PP_RECORD_SPLIT_BY_TIME); //1 - auto start
						int rec_split_time = 30;	
						player_record.RecordSetup(getRecordPath(), record_flags, rec_split_time, 0, "");
						player_record.RecordStart();
						player_record.Play();
					}else{
						//stop recording
						player_record.RecordStop();
					}
				}*/
				
				if(is_record){
					//start recording
					if(player != null){
						int record_flags = get_record_flags();
						int rec_split_time = ( (record_flags & PlayerRecordFlags.forType(PlayerRecordFlags.PP_RECORD_SPLIT_BY_TIME)) != 0 )? 15:0; //15 sec	
						player.RecordSetup(getRecordPath(), record_flags, rec_split_time, 0, "");
						player.RecordStart();
					}
				}else{
					//stop recording
					if(player != null){
						player.RecordStop();
					}
				}
				
				ImageView ivLed  = (ImageView)findViewById(R.id.led);
				if(ivLed != null)
					ivLed.setImageResource( ( is_record ? R.drawable.led_red : R.drawable.led_green) ); 
				btnRecord.setText( is_record? "Stop Record":"Start Record" );
			}
        });
        
        
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.main_view);
        layout.setOnTouchListener(new OnTouchListener() 
		{
			public boolean onTouch(View v, MotionEvent event) 
			{
				InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				if (getWindow() != null && getWindow().getCurrentFocus() != null && getWindow().getCurrentFocus().getWindowToken() != null)
					inputManager.hideSoftInputFromWindow(getWindow().getCurrentFocus().getWindowToken(), 0);
				return true;
			}
		});
        
		playerStatusText.setText("VXG Face Detection Demo");
		setShowControls();
        
    }

    private int[] mColorSwapBuf = null;                        // used by saveFrame()
    public Bitmap getFrameAsBitmap(ByteBuffer frame, int width, int height)
    {
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(frame);
        return bmp;
    }

	int get_record_flags()
	{
		int flags = PlayerRecordFlags.forType(PlayerRecordFlags.PP_RECORD_AUTO_START) | PlayerRecordFlags.forType(PlayerRecordFlags.PP_RECORD_SPLIT_BY_TIME);	// auto start and split by time
		//+ audio only
		//flags |= PlayerRecordFlags.forType(PlayerRecordFlags.PP_RECORD_DISABLE_VIDEO);	
		
		//+ PTS correction
		flags |= PlayerRecordFlags.forType(PlayerRecordFlags.PP_RECORD_PTS_CORRECTION);	
		return flags;	
	}
		
    
    public void onClick(View v) 
	{
		SharedSettings.getInstance().loadPrefSettings();
		if (player != null)
		{
			if (!edtIpAddressHistory.contains(player.getConfig().getConnectionUrl()))
				edtIpAddressHistory.add(player.getConfig().getConnectionUrl());
			
			player.getConfig().setConnectionUrl(edtIpAddress.getText().toString());
			if (player.getConfig().getConnectionUrl().isEmpty())
				return;

			if (toastShot != null)
				toastShot.cancel();
			
			//player_record.Close();
			
			player.Close();
			if (playing)
			{
    			setUIDisconnected();
			}
			else
			{
    	    	SharedSettings sett = SharedSettings.getInstance();

    	    	
    	    	MediaPlayerConfig conf = new MediaPlayerConfig();
    	    	
    	    	player.setVisibility(View.INVISIBLE);
    	    	
    	    	conf.setConnectionUrl(player.getConfig().getConnectionUrl());
    			
    	    	conf.setConnectionNetworkProtocol(sett.connectionProtocol);
    	    	conf.setConnectionDetectionTime(sett.connectionDetectionTime);
    	    	conf.setConnectionBufferingTime(sett.connectionBufferingTime);
    	    	conf.setDecodingType(2/*sett.decoderType*/);
				conf.setSynchroEnable(sett.synchroEnable);
    	    	conf.setSynchroNeedDropVideoFrames(sett.synchroNeedDropVideoFrames);
				conf.setEnableAspectRatio(sett.rendererEnableAspectRatio);
    	    	conf.setDataReceiveTimeout(30000);
    	    	conf.setNumberOfCPUCores(0);
				
    	    	
    	    	//record config
    	    	if(is_record){
					int record_flags = get_record_flags();
					int rec_split_time = ( (record_flags & PlayerRecordFlags.forType(PlayerRecordFlags.PP_RECORD_SPLIT_BY_TIME)) != 0)? 15:0; //15 sec	
    	    		conf.setRecordPath(getRecordPath());
    	    		conf.setRecordFlags(record_flags);
    	    		conf.setRecordSplitTime(rec_split_time);
    	    		conf.setRecordSplitSize(0);
    	    	}else{
    	    		conf.setRecordPath("");
    	    		conf.setRecordFlags(0);
    	    		conf.setRecordSplitTime(0);
    	    		conf.setRecordSplitSize(0);
    	    	}
    	    	Log.v(TAG, "conf record="+is_record+" getRecordFlags()="+conf.getRecordFlags());


				player.setOnDataListener(mthis);
				// Open Player	
        	    player.Open(conf, mthis);

				btnConnect.setText("Disconnect");
				
				
				//record only
				conf.setMode(PlayerModes.PP_MODE_RECORD);
				//conf.setRecordTrimPosStart(10000); //from 10th sec
				//conf.setRecordTrimPosEnd(20000); //to 20th sec 
				/*player_record.Open(conf, new MediaPlayerCallback(){

					@Override
					public int Status(int arg) {
						Log.i(TAG, "=player_record Status arg="+arg);
						return 0;
					}

					@Override
					public int OnReceiveData(ByteBuffer buffer, int size,
							long pts) {
						// TODO Auto-generated method stub
						return 0;
					}
					
				});*/
				
				
				playing = true;
			}
		}
    }
 
	protected void onPause()
	{
		Log.e("SDL", "onPause()");
		super.onPause();

		editor = settings.edit();
		editor.putString("connectionUrl", edtIpAddress.getText().toString());

		editor.putStringSet("connectionHistory", edtIpAddressHistory);
		editor.commit();
		
		if (player != null)
			player.onPause();
	}

	@Override
  	protected void onResume() 
	{
		Log.e("SDL", "onResume()");
		super.onResume();
		if (player != null)
			player.onResume();
  	}

  	@Override
	protected void onStart() 
  	{
      	Log.e("SDL", "onStart()");
		super.onStart();
		if (player != null)
			player.onStart();
	}

  	@Override
	protected void onStop() 
  	{
  		Log.e("SDL", "onStop()");
		super.onStop();
		if (player != null)
			player.onStop();
		
		if (toastShot != null)
			toastShot.cancel();

	}

    @Override
    public void onBackPressed() 
    {
		if (toastShot != null)
			toastShot.cancel();
		
		player.Close();
		if (!playing)
		{
	  		super.onBackPressed();
	  		return;			
		}

		setUIDisconnected();
    }
  	
  	@Override
  	public void onWindowFocusChanged(boolean hasFocus) 
  	{
  		Log.e("SDL", "onWindowFocusChanged(): " + hasFocus);
  		super.onWindowFocusChanged(hasFocus);
		if (player != null)
			player.onWindowFocusChanged(hasFocus);
  	}

  	@Override
  	public void onLowMemory() 
  	{
  		Log.e("SDL", "onLowMemory()");
  		super.onLowMemory();
		if (player != null)
			player.onLowMemory();
  	}

  	@Override
  	protected void onDestroy() 
  	{
  		Log.e("SDL", "onDestroy()");
		if (toastShot != null)
			toastShot.cancel();
		
		if (player != null)
			player.onDestroy();
		

		System.gc();
		
		if (multicastLock != null) {
		    multicastLock.release();
		    multicastLock = null;
		}		
		super.onDestroy();
   	}	
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) 
    {
		super.onCreateOptionsMenu(menu);

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item)  
	{
		switch (item.getItemId())  
		{
			case R.id.main_opt_settings:   
		
				SharedSettings.getInstance().loadPrefSettings();

				Intent intentSettings = new Intent(MainActivity.this, PreferencesActivity.class);     
				startActivity(intentSettings);

				break;
			case R.id.main_opt_clearhistory:     
			
				new AlertDialog.Builder(this)
				.setTitle("Clear History")
				.setMessage("Do you really want to delete the history?")
				.setPositiveButton("Yes", new DialogInterface.OnClickListener() 
				{
					public void onClick(DialogInterface dialog, int which) 
					{

						edtIpAddressHistory.clear();

					}
				})
				.setNegativeButton("No", new DialogInterface.OnClickListener() 
				{
					public void onClick(DialogInterface dialog, int which) 
					{
						// do nothing
					}
				}).show();
				break;
			case R.id.main_opt_exit:     
				finish();
				break;

		}
		return true;
	}

	protected void setUIDisconnected()
	{
		setTitle(R.string.app_name);
		btnConnect.setText("Connect");
		playing = false;
	}

	protected void setHideControls()
	{
		btnShot.setVisibility(View.VISIBLE);
		edtIpAddress.setVisibility(View.GONE);
		btnHistory.setVisibility(View.GONE);
		btnConnect.setVisibility(View.GONE);
	}

	protected void setShowControls()
	{
		setTitle(R.string.app_name);
		
		btnShot.setVisibility(View.GONE);
		edtIpAddress.setVisibility(View.VISIBLE);
		btnHistory.setVisibility(View.VISIBLE);
		btnConnect.setVisibility(View.VISIBLE);
	}

	private void showStatusView() 
	{
		player.setVisibility(View.INVISIBLE);
		playerHwStatus.setVisibility(View.INVISIBLE);
		//player.setAlpha(0.0f);
		playerStatus.setVisibility(View.VISIBLE);
		
	}
	
	private void showVideoView() 
	{
        playerStatus.setVisibility(View.INVISIBLE);
 		player.setVisibility(View.VISIBLE);
		playerHwStatus.setVisibility(View.VISIBLE);

 		SurfaceHolder sfhTrackHolder = player.getSurfaceView().getHolder();
		sfhTrackHolder.setFormat(PixelFormat.TRANSPARENT);
		
		setTitle("");
	}
    



	
    static public <T> void executeAsyncTask(AsyncTask<T, ?, ?> task, T... params) 
    {
    	{
    		task.execute(params);
    	}
    }  
	



	@Override
	public int OnVideoSourceFrameAvailable(ByteBuffer buffer, int size,
			long pts, long dts, int stream_index, int format) {
		// TODO Auto-generated method stub
		return 0;
	}


	@Override
	public int OnAudioSourceFrameAvailable(ByteBuffer buffer, int size,
			long pts, long dts, int stream_index, int format) {
		// TODO Auto-generated method stub
		return 0;
	}
	
}

package ml.tasneem.heartbeat;


import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends Activity {

    Button bHeart, bHeal, bStop;
    TextView tTop;
    MediaPlayer mPlayer = null;
    SeekBar seekbar;
    Handler updateSeekbarHandler = new Handler();
    int pauseTimeHeart = 0, pauseTimeHeal = 0, curResPlaying = -1;

    /* Some documentation on the song files
     - heal_the_world_1 - Original song by Michael Jackson
     - heartbeat_1 -
     */
    int R_heal_the_world = R.raw.heal_the_world_1;
    int R_heart_beat = R.raw.heartbeat_1;

    TextView tSoundLevel;
    ToggleButton bSound;
    Handler updateSongHandler = new Handler();
    AudioRecord audioRecord;
    ProgressBar progressBar;
    SeekBar soundThresholdBar;
    int MAX_SOUND_LEVEL = 32768; // Same as "short"
    private Runnable UpdateSeekbarPositionRunnable = new Runnable() {
        public void run() {
            int curTime = 0;
            if (mPlayer != null && mPlayer.isPlaying()) {
                curTime = mPlayer.getCurrentPosition();
            }
            seekbar.setProgress(curTime);
            updateSeekbarHandler.postDelayed(this, 100);
        }
    };
    private Runnable UpdateSongRunnable = new Runnable() {
        public void run() {
            int readBufSize = audioRecord.getSampleRate() / 10;
            short[] recordedData = new short[readBufSize];
            int numData = audioRecord.read(recordedData, 0, readBufSize);
            if (numData > 0) {
                double lastData = rms(recordedData, numData);
                int soundLvl = (int) lastData;
                progressBar.setProgress(soundLvl);
                tSoundLevel.setText("Sound Level: " + soundLvl + " / " + MAX_SOUND_LEVEL +
                        " Threshold: " + soundThresholdBar.getProgress());
                if (soundLvl > soundThresholdBar.getProgress()) {
                    if (Build.VERSION.SDK_INT >= 21) {
                        progressBar.setProgressTintList(ColorStateList.valueOf(Color.RED));
                    } else {
                        progressBar.getProgressDrawable().setColorFilter(Color.RED, PorterDuff.Mode.MULTIPLY);
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= 21) {
                        progressBar.setProgressTintList(ColorStateList.valueOf(Color.GREEN));
                    } else {
                        progressBar.getProgressDrawable().setColorFilter(Color.GREEN, PorterDuff.Mode.MULTIPLY);
                    }
                }
            }
            updateSongHandler.postDelayed(this, 50);
        }
    };

    public static double rms(short[] nums, int length) {
        double ms = 0;
        for (int i = 0; i < length; i++) {
            double n = nums[i];
            ms += n * n;
        }
        ms /= length;
        return Math.sqrt(ms);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tTop = (TextView) findViewById(R.id.text_top);
        tTop.setText("Application has started ...");

        bHeart = (Button) findViewById(R.id.button_heart);
        bHeal = (Button) findViewById(R.id.button_heal);
        bStop = (Button) findViewById(R.id.button_stop);

        seekbar = (SeekBar) findViewById(R.id.seek_bar);
        updateSeekbarHandler.postDelayed(UpdateSeekbarPositionRunnable, 100);

        bHeart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playResource(R_heart_beat);
                tTop.setText("Playing Heart Beat");
            }
        });
        bHeal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playResource(R_heal_the_world);
                tTop.setText("Playing Heal The World");
            }
        });
        bStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playResource(-1);
                tTop.setText("Stopped playing music");
            }
        });
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && mPlayer != null && mPlayer.isPlaying()) {
                    mPlayer.seekTo(progress);
                }
            }

            public void onStartTrackingTouch(SeekBar bar) {
            }

            public void onStopTrackingTouch(SeekBar bar) {
            }
        });

        /*
            Recorder stuff
        */
        audioRecord = findAudioRecord();
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        progressBar.setMax(MAX_SOUND_LEVEL);
        soundThresholdBar = (SeekBar) findViewById(R.id.sound_threshold_bar);
        soundThresholdBar.setMax(MAX_SOUND_LEVEL);
        soundThresholdBar.setProgress(MAX_SOUND_LEVEL / 2);
        tSoundLevel = (TextView) findViewById(R.id.text_sound_level);

        bSound = (ToggleButton) findViewById(R.id.button_sound);
        bSound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (audioRecord.getState() != AudioRecord.STATE_UNINITIALIZED) {
                    if (bSound.isChecked()) {
                        audioRecord.startRecording(); // called on an uninitialized audiorecord ?
                        updateSongHandler.postDelayed(UpdateSongRunnable, 100);
                    } else {
                        audioRecord.stop();
                        updateSongHandler.removeCallbacks(UpdateSongRunnable);
                    }
                } else {
                    tTop.setText("Auido Record was not initialized !");
                }
            }
        });
//        soundThresholdBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
//            }
//            public void onStartTrackingTouch(SeekBar bar) {}
//            public void onStopTrackingTouch(SeekBar bar) {}
//        });

    }

    /*
        If resid is set to -1, no resource is played. Else, the new resource
        is played.
     */
    void playResource(int resid) {
        if (curResPlaying == resid) {
            return;
        }
        if (mPlayer != null) {  // delete last media player if it exists
            if (curResPlaying == R_heal_the_world) {
                pauseTimeHeal = mPlayer.getCurrentPosition();
            } else if (curResPlaying == R_heart_beat) {
                pauseTimeHeart = mPlayer.getCurrentPosition();
            }
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
            seekbar.setClickable(false);
        }
        curResPlaying = resid;
        if (resid != -1) {  // -1 is to stop the mplayer completely
            mPlayer = MediaPlayer.create(this, resid);
            mPlayer.setLooping(true);
            mPlayer.start();
            if (resid == R_heal_the_world) {
                mPlayer.seekTo(pauseTimeHeal);
            } else if (resid == R_heart_beat) {
                mPlayer.seekTo(pauseTimeHeart);
            }
            seekbar.setMax(mPlayer.getDuration());
            seekbar.setProgress(mPlayer.getCurrentPosition());
            seekbar.setClickable(true);
        }
    }

    private AudioRecord findAudioRecord() {
        int[] samplingRateArray = new int[]{8000, 11025, 22050, 44100}; // in Hz
        int[] audioFormatArray = new int[]{AudioFormat.ENCODING_PCM_16BIT,
                AudioFormat.ENCODING_PCM_8BIT};
        for (int samplingRate : samplingRateArray) {
            for (int audioFormat : audioFormatArray) {
                int audioSource = MediaRecorder.AudioSource.MIC;
                int channelConfig = AudioFormat.CHANNEL_IN_MONO;
                try {
                    Log.d("HeartBeat", "Attempting rate " + samplingRate + "Hz, bits: "
                            + audioFormat + ", channel: " + channelConfig);

                    int bufferSize = AudioRecord.getMinBufferSize(samplingRate, channelConfig,
                            audioFormat) + 1024 * 16;
                    if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
                        AudioRecord aRecord = new AudioRecord(audioSource,
                                samplingRate, channelConfig, audioFormat, bufferSize);
                        if (aRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                            tTop.setText("FOUND rate: " + samplingRate + "Hz, bits: "
                                    + audioFormat + ", channel: " + channelConfig);

                            if (Build.VERSION.SDK_INT > 16 && NoiseSuppressor.isAvailable()) {
                                Log.d("HeartBeat", "Enabling Noise suppresor");
                                NoiseSuppressor noiseSuppress = NoiseSuppressor.create(aRecord.getAudioSessionId());
                                noiseSuppress.setEnabled(true);
                            }
                            return aRecord;
                        }
                    }
                } catch (Exception e) {
                    Log.e("HeartBeat", samplingRate + " Exception. Keep trying.", e);
                }
            }
        }
        Log.e("HeartBeat", "ERROR: Was unable to find a compatible AudioRecord !");
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public void onDestroy() {
        playResource(-1);
        audioRecord.stop();
        audioRecord.release();
        super.onDestroy();
    }
}

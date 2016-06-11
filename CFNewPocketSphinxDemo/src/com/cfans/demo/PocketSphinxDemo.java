package com.cfans.demo;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;
import edu.cmu.pocketsphinx.demo.R;

public class PocketSphinxDemo extends Activity implements OnTouchListener, RecognitionListener {

	String TAG = "PocketSphinxDemo ";

	static {
		System.loadLibrary("pocketsphinx_jni");
	}

	TextView performance_text;

	EditText edit_text;

	private SpeechRecognizer recognizer;

	public boolean onTouch(View v, MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			edit_text.setText("");
			recognizer.startListening("Test");
			break;
		case MotionEvent.ACTION_UP:
			recognizer.stop();

			break;
		default:
			;
		}
		/* Let the button handle its own state */
		return false;
	}

	/** Called when the activity is first created. */
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		Button b = (Button) findViewById(R.id.Button01);
		b.setOnTouchListener(this);
		this.performance_text = (TextView) findViewById(R.id.PerformanceText);
		this.edit_text = (EditText) findViewById(R.id.EditText01);

		boolean isChinese = false;

		try {
			String hmmPath = null;
			String commandDicPath = null;
			String commandGramPath = null;

			SpeechRecognizerSetup recognizerSetup = SpeechRecognizerSetup.defaultSetup();
			if (isChinese) {
				
				hmmPath = App.POCKETSPHIN_DIR + "/hmm/zh";
				commandDicPath = App.POCKETSPHIN_DIR + "/lm/commands_zh.dic";
				commandGramPath =  App.POCKETSPHIN_DIR + "/lm/commands_zh.gram";

			} else {
				
				hmmPath = App.POCKETSPHIN_DIR + "/hmm/en";
				commandDicPath = App.POCKETSPHIN_DIR + "/lm/commands_en.dic";
				commandGramPath =  App.POCKETSPHIN_DIR + "/lm/commands_en.gram";
			}
			recognizerSetup.setString("-hmm", hmmPath);
			recognizerSetup.setString("-dict", commandDicPath);
			recognizerSetup.setKeywordThreshold(1e-45f);
			recognizerSetup.setBoolean("-allphone_ci", true);
			recognizer = recognizerSetup.getRecognizer();
			recognizer.addGrammarSearch("Test", new File(commandGramPath));
			recognizer.addListener(this);
			} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onBeginningOfSpeech() {
		Log.e(TAG, "onBeginning");
	}

	@Override
	public void onEndOfSpeech() {
		Log.e(TAG, "onEndOfSpeech");

	}

	@Override
	public void onError(Exception arg0) {
		Log.e(TAG, "onError : " + arg0);

	}

	@Override
	public void onPartialResult(Hypothesis arg0) {
		if (arg0 != null) {
			Log.e(TAG, "onPartialResult : " + arg0.getHypstr());
			Log.e(TAG, "onPartialResult : " + arg0.getBestScore());
			Log.e(TAG, "onPartialResult : " + arg0.getProb());
		}
		Log.e(TAG, "onPartialResult : " + arg0);


	}

	@Override
	public void onResult(Hypothesis arg0) {
		if (arg0 != null) {
			edit_text.setText(arg0.getHypstr());
			Log.e(TAG, "onResult : " + arg0.getHypstr());
			Log.e(TAG, "onResult : " + arg0.getBestScore());
			Log.e(TAG, "onResult : " + arg0.getProb());
		}
		Log.e(TAG, "onResult : " + arg0);

	}

	@Override
	public void onTimeout() {
		Log.e(TAG, "onTimeout : ");

	}
}
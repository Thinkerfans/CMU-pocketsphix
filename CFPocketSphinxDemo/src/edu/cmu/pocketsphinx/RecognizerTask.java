package edu.cmu.pocketsphinx;

import java.util.concurrent.LinkedBlockingQueue;

import com.cfans.demo.App;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

/**
 * Speech recognition task, which runs in a worker thread.
 * 
 * This class implements speech recognition for this demo application. It takes
 * the form of a long-running task which accepts requests to start and stop
 * listening, and emits recognition results to a listener.
 * 
 * @author David Huggins-Daines <dhuggins@cs.cmu.edu>
 */
public class RecognizerTask implements Runnable {
	
	/**
	 * State of the main loop.
	 */
	enum State {
		IDLE, LISTENING
	};

	/**
	 * Events for main loop.
	 */
	enum Event {
		NONE, START, STOP, SHUTDOWN
	};
	
	
	/**
	 * PocketSphinx native decoder object.
	 */
	Decoder mPSDecoder;
	/**
	 * Audio recording task.
	 */
	AudioTask mAudioTask;
	/**
	 * Thread associated with recording task.
	 */
	Thread mAudioThread;
	/**
	 * Queue of audio buffers.
	 */
	LinkedBlockingQueue<short[]> mAudioQueue;
	/**
	 * Listener for recognition results.
	 */
	RecognitionListener mRecognitionListener;
	/**
	 * Whether to report partial results.
	 */
	boolean mPartialsEnable;

	/**
	 * Current event.
	 */
	Event mMailbox;

	public RecognitionListener getRecognitionListener() {
		return mRecognitionListener;
	}

	public void setRecognitionListener(RecognitionListener rl) {
		this.mRecognitionListener = rl;
	}

	public void setUsePartials(boolean use_partials) {
		this.mPartialsEnable = use_partials;
	}

	public boolean getUsePartials() {
		return this.mPartialsEnable;
	}
	
	public static String SDCARD_DIR = Environment.getExternalStorageDirectory()
			.getAbsolutePath();

	public RecognizerTask(boolean isChinese) {
		pocketsphinx
				.setLogfile(Config.POCKETSPHIN_DIR +"/pocketsphinx.log");
		Config c = new Config();
		/*
		 * In 2.2 and above we can use getExternalFilesDir() or whatever it's
		 * called
		 */
	
		if (isChinese) {
			c.setString("-hmm", Config.POCKETSPHIN_DIR+"/hmm/zh/tdt_sc_8k");
		}else{
			c.setString("-hmm", Config.POCKETSPHIN_DIR+"/hmm/en");
		}
		c.setString("-dict",Config.POCKETSPHIN_DIR+"/lm/command.dic");
		c.setString("-lm", Config.POCKETSPHIN_DIR+"/lm/command.lm");
//		
//		c.setString("-hmm", SDCARD_DIR+"/pocketsphinx/hmm/zh/tdt_sc_8k");
//		c.setString("-hmm", SDCARD_DIR+"/pocketsphinx/hmm/en_US/hub4wsj_sc_8k");
//		c.setString("-hmm", SDCARD_DIR+"/pocketsphinx/hmm/en/tidigits");


//		c.setString("-dict", SDCARD_DIR+"/pocketsphinx/lm/command.dic");
//		c.setString("-lm", SDCARD_DIR+"/pocketsphinx/lm/command.lm");
		Log.e("test: ", " "+ Config.POCKETSPHIN_DIR+"/hmm/zh/tdt_sc_8k");
		c.setFloat("-samprate", 8000.0);
		c.setInt("-maxhmmpf", 2000);
		c.setInt("-maxwpf", 10);
		c.setInt("-pl_window", 2);
		c.setBoolean("-backtrace", true);
		c.setBoolean("-bestpath", false);
		this.mPSDecoder = new Decoder(c);
		this.mAudioTask = null;
		this.mAudioQueue = new LinkedBlockingQueue<short[]>();
		this.mPartialsEnable = false;
		this.mMailbox = Event.NONE;
	}
	
	

	public void start() {
		Log.d(getClass().getName(), "signalling START");
		synchronized (this.mMailbox) {
			this.mMailbox.notifyAll();
			Log.d(getClass().getName(), "signalled START");
			this.mMailbox = Event.START;
		}
	}

	public void stop() {
		Log.d(getClass().getName(), "signalling STOP");
		synchronized (this.mMailbox) {
			this.mMailbox.notifyAll();
			Log.d(getClass().getName(), "signalled STOP");
			this.mMailbox = Event.STOP;
		}
	}

	public void shutdown() {
		Log.d(getClass().getName(), "signalling SHUTDOWN");
		synchronized (this.mMailbox) {
			this.mMailbox.notifyAll();
			Log.d(getClass().getName(), "signalled SHUTDOWN");
			this.mMailbox = Event.SHUTDOWN;
		}
	}

	public void run() {
		/* Main loop for this thread. */
		boolean done = false;
		/* State of the main loop. */
		State state = State.IDLE;
		/* Previous partial hypothesis. */
		String partial_hyp = null;

		while (!done) {
			/* Read the mail. */
			Event todo = Event.NONE;
			synchronized (this.mMailbox) {
				todo = this.mMailbox;
				/* If we're idle then wait for something to happen. */
				if (state == State.IDLE && todo == Event.NONE) {
					try {
						Log.d(getClass().getName(), "waiting");
						this.mMailbox.wait();
						todo = this.mMailbox;
						Log.d(getClass().getName(), "got" + todo);
					} catch (InterruptedException e) {
						/* Quit main loop. */
						Log.e(getClass().getName(),
								"Interrupted waiting for mailbox, shutting down");
						todo = Event.SHUTDOWN;
					}
				}
				/* Reset the mailbox before releasing, to avoid race condition. */
				this.mMailbox = Event.NONE;
			}
			/* Do whatever the mail says to do. */
			switch (todo) {
			case NONE:
				if (state == State.IDLE)
					Log.e(getClass().getName(),
							"Received NONE in mailbox when IDLE, threading error?");
				break;
			case START:
				if (state == State.IDLE) {
					this.mAudioTask = new AudioTask(this.mAudioQueue, 1024);
					this.mAudioThread = new Thread(this.mAudioTask);
					Log.d(getClass().getName(), "START");

					this.mPSDecoder.startUtt();
					Log.d(getClass().getName(), "START OK");

					this.mAudioThread.start();
					state = State.LISTENING;
				} else
					Log.e(getClass().getName(),
							"Received START in mailbox when LISTENING");
				break;
			case STOP:
				if (state == State.IDLE)
					Log.e(getClass().getName(),
							"Received STOP in mailbox when IDLE");
				else {
					Log.d(getClass().getName(), "STOP");
					assert this.mAudioTask != null;
					this.mAudioTask.stop();
					try {
						this.mAudioThread.join();
					} catch (InterruptedException e) {
						Log.e(getClass().getName(),
								"Interrupted waiting for audio thread, shutting down");
						done = true;
					}
					/* Drain the audio queue. */
					short[] buf;
					while ((buf = this.mAudioQueue.poll()) != null) {
						Log.d(getClass().getName(), "Reading " + buf.length
								+ " samples from queue");
						this.mPSDecoder.processRaw(buf, buf.length, false, false);
					}
					this.mPSDecoder.endUtt();
					this.mAudioTask = null;
					this.mAudioThread = null;

					Hypothesis hyp = this.mPSDecoder.getHyp();
					if (this.mRecognitionListener != null) {
						if (hyp == null) {
							Log.d(getClass().getName(), "Recognition failure");
							this.mRecognitionListener.onError(-1);
						} else {
							Bundle b = new Bundle();
							Log.e(getClass().getName(),
									"zs log Final hypothesis: "
											+ hyp.getHypstr());
							Log.e(getClass().getName(), mPSDecoder.getUttid());
							b.putString("hyp", hyp.getHypstr());
							this.mRecognitionListener.onResults(b);
						}
					}
					state = State.IDLE;
				}
				break;
			case SHUTDOWN:
				Log.d(getClass().getName(), "SHUTDOWN");
				if (this.mAudioTask != null) {
					this.mAudioTask.stop();
					assert this.mAudioThread != null;
					try {
						this.mAudioThread.join();
					} catch (InterruptedException e) {
						/* We don't care! */
					}
				}
				this.mPSDecoder.endUtt();
				this.mAudioTask = null;
				this.mAudioThread = null;
				state = State.IDLE;
				done = true;
				break;
			}
			/*
			 * Do whatever's appropriate for the current state. Actually this
			 * just means processing audio if possible.
			 */
			if (state == State.LISTENING) {
				assert this.mAudioTask != null;
				try {
					short[] buf = this.mAudioQueue.take();
					Log.d(getClass().getName(), "Reading " + buf.length
							+ " samples from queue");
					this.mPSDecoder.processRaw(buf, buf.length, false, false);
					Hypothesis hyp = this.mPSDecoder.getHyp();
					if (hyp != null) {
						String hypstr = hyp.getHypstr();
						if (hypstr != partial_hyp) {
							Log.d(getClass().getName(),
									"Hypothesis: " + hyp.getHypstr()
											+ " Uttid: " + hyp.getUttid()
											+ " Best_score: "
											+ hyp.getBest_score());
							if (this.mRecognitionListener != null && hyp != null) {
								Bundle b = new Bundle();
								b.putString("hyp", hyp.getHypstr());
								this.mRecognitionListener.onPartialResults(b);
							}
						}
						partial_hyp = hypstr;
					}
				} catch (InterruptedException e) {
					Log.d(getClass().getName(), "Interrupted in audioq.take");
				}
			}
		}
	}

	
	
	/**
	 * Audio recording task.
	 * 
	 * This class implements a task which pulls blocks of audio from the system
	 * audio input and places them on a queue.
	 * 
	 * @author David Huggins-Daines <dhuggins@cs.cmu.edu>
	 */
	class AudioTask implements Runnable {
		/**
		 * Queue on which audio blocks are placed.
		 */
		LinkedBlockingQueue<short[]> q;
		AudioRecord rec;
		int block_size;
		boolean done;

		static final int DEFAULT_BLOCK_SIZE = 512;

		AudioTask() {
			this.init(new LinkedBlockingQueue<short[]>(), DEFAULT_BLOCK_SIZE);
		}

		AudioTask(LinkedBlockingQueue<short[]> q) {
			this.init(q, DEFAULT_BLOCK_SIZE);
		}

		AudioTask(LinkedBlockingQueue<short[]> q, int block_size) {
			this.init(q, block_size);
		}

		void init(LinkedBlockingQueue<short[]> q, int block_size) {
			this.done = false;
			this.q = q;
			this.block_size = block_size;
			this.rec = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, 8000,
					AudioFormat.CHANNEL_IN_MONO,
					AudioFormat.ENCODING_PCM_16BIT, 8192);
		}

		public int getBlockSize() {
			return block_size;
		}

		public void setBlockSize(int block_size) {
			this.block_size = block_size;
		}

		public LinkedBlockingQueue<short[]> getQueue() {
			return q;
		}

		public void stop() {
			this.done = true;
		}

		public void run() {
			this.rec.startRecording();
			while (!this.done) {
				int nshorts = this.readBlock();
				if (nshorts <= 0)
					break;
			}
			this.rec.stop();
			this.rec.release();
		}

		int readBlock() {
			short[] buf = new short[this.block_size];
			int nshorts = this.rec.read(buf, 0, buf.length);
			if (nshorts > 0) {
				this.q.add(buf);
			}
			return nshorts;
		}
	}//end AudioTask

	
}
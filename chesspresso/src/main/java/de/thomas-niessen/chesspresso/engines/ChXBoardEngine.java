/*******************************************************************************
 * Basic version: Copyright (C) 2003 Bernhard Seybold. All rights reserved.
 * All changes since then: Copyright (C) 2019 Thomas Niessen. All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 ******************************************************************************/
package chesspresso.engines;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;

import chesspresso.move.Move;
//import chesspresso.*;
import chesspresso.position.FEN;
import chesspresso.position.ImmutablePosition;

/**
 *
 * @author Bernhard Seybold
 * @version $Revision: 1.1 $
 */
public class ChXBoardEngine {
	public interface Listener {
		public void notifyInputMessage(String msg);

		public void notifyEngineMessage(String msg);
	}

	public interface AnalysisListener {
		public void notifyPeriodicUpdate(int time, int nodes, int ply, int mvleft, int mvtot, String mvname);

		public void notifyPost(int ply, int score, int time, int nodesSearched, String bestLine);
	}

	private class EngineMessageListener implements Runnable {
		public void run() {
			try {
				for (;;) {
					if (m_process == null)
						return; // =====>
//                    if (m_listen) {
					String line = waitForAnswer(50, 1);
					if (line != null) {
						dispatch(line);
					}
//                    }
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	/* ================================================================================ */

	private Process m_process;
	private BufferedReader m_in;
	private Writer m_out;
	private Vector<Listener> m_listeners;
	private Thread m_thread;
	private Hashtable<String, String> m_features;
	private boolean m_inAnalyzeMode;
	private AnalysisListener m_analysisListener;

	/* ================================================================================ */

	public ChXBoardEngine(String command, String dir) throws IOException {
		m_features = new Hashtable<String, String>();
		m_process = Runtime.getRuntime().exec(command, null, new File(dir));
		m_in = new BufferedReader(new InputStreamReader(m_process.getInputStream()));
//        m_out = new BufferedWriter(new OutputStreamWriter(m_process.getOutputStream()));
		m_out = new OutputStreamWriter(m_process.getOutputStream());
		m_inAnalyzeMode = false;
		m_analysisListener = null;

		m_thread = new Thread(new EngineMessageListener());
		m_thread.start();
	}

	/* ================================================================================ */

	public void addListener(Listener listener) {
		if (m_listeners == null)
			m_listeners = new Vector<Listener>();
		m_listeners.add(listener);
	}

	public void removeListener(Listener listener) {
		m_listeners.remove(listener);
		if (m_listeners.size() == 0)
			m_listeners = null;
	}

	private void fireInputMessage(String msg) {
//        System.out.println("> " + msg);
		if (m_listeners != null) {
			for (Enumeration<Listener> e = m_listeners.elements(); e.hasMoreElements();) {
				((Listener) e.nextElement()).notifyInputMessage(msg);
			}
		}
	}

	private void fireEngineMessage(String msg) {
//        System.out.println("< " + msg);
		if (m_listeners != null) {
			for (Enumeration<Listener> e = m_listeners.elements(); e.hasMoreElements();) {
				((Listener) e.nextElement()).notifyEngineMessage(msg);
			}
		}
	}

	/* ================================================================================ */

	private void addFeature(String name, String value) {
//        System.out.println("feature " + name + " = " + value);
		m_features.put(name, value);
	}

	private String getFeature(String name) {
		return (String) m_features.get(name);
	}

	/* ================================================================================ */

	private void parseFeatureMessage(String line) {
		StringTokenizer tokenizer = new StringTokenizer(line);
		tokenizer.nextToken(); // consume 'feature'
		while (tokenizer.hasMoreTokens()) {
			String pair = tokenizer.nextToken();
			int ind = pair.indexOf('=');
			String name = pair.substring(0, ind);
			String value = pair.substring(ind + 1);
			if (value.startsWith("\"") && value.endsWith("\"")) {
				addFeature(name, value.substring(1, value.length() - 1));
			} else {
				addFeature(name, value);
			}
		}
	}

	private void dispatch(String line) {
		if (line.startsWith("feature")) {
			parseFeatureMessage(line);
		} else {
			if (m_analysisListener != null) {
				if (line.startsWith("stat01:")) {
				} else {
					StringTokenizer tokenizer = new StringTokenizer(line);
					try {
						int ply = Integer.parseInt(tokenizer.nextToken());
						int score = Integer.parseInt(tokenizer.nextToken());
						int time = Integer.parseInt(tokenizer.nextToken());
						int nodesSearched = Integer.parseInt(tokenizer.nextToken());
						StringBuffer bestLine = new StringBuffer(tokenizer.nextToken());
						while (tokenizer.hasMoreTokens())
							bestLine.append(" ").append(tokenizer.nextToken());
						m_analysisListener.notifyPost(ply, score, time, nodesSearched, bestLine.toString());
					} catch (Exception ex) {
					}
				}
			}
		}
		fireEngineMessage(line);
	}

	/* ================================================================================ */

	public synchronized String getName() {
		return getFeature("myname");
	}

	public synchronized void init() {
		final int WAIT = 2000; // wait two seconds for features like xboard

//        sendMessage("xboard");
		sendMessage("log off"); // is this only crafty?
		sendMessage("protover 2");

		// wait for features
		long time = System.currentTimeMillis();
		while (System.currentTimeMillis() < time + WAIT && !"1".equals(getFeature("done"))) {
			try {
				wait(100);
			} catch (InterruptedException ex) {
			}
		}

		sendMessage("new");
	}

	public synchronized void doMove(Move move) {
		if (m_inAnalyzeMode) {
			sendMessage(move.toString());
		} else {
			sendMessage("MOVE " + move);
		}
	}

	public synchronized void undoMove() {
		sendMessage("undo");
	}

	public synchronized void analyze(AnalysisListener listener, boolean post, int periodicUpdateInterval) {
		if (!m_inAnalyzeMode) {
			m_inAnalyzeMode = true;
			m_analysisListener = listener;
			if (post)
				sendMessage("post");
			sendMessage("analyze");
			if (periodicUpdateInterval > 0) {
			}
		}
	}

	public synchronized void unanalyze() {
		if (m_inAnalyzeMode) {
			sendMessage("exit");
			m_analysisListener = null;
			m_inAnalyzeMode = false;
		}
	}

	public synchronized void quit() {
		sendMessage("quit");
		Process process = m_process;
		m_process = null; // stops thread
		process.destroy();
	}

	public synchronized void setPosition(ImmutablePosition pos) {
//        System.out.println(getFeature("setboard"));
		if ("1".equals(getFeature("setboard"))) {
			sendMessage("setboard " + FEN.getFEN(pos));
		}
	}

	public synchronized void setHashSize(int hashSize) {
		sendMessage("hash " + hashSize + "M");
	}

	/* ================================================================================ */

	private String listen() throws IOException {
		return m_in.readLine();
	}

	private String waitForAnswer(long waitTime, int numOfRetries) throws IOException {
		for (int i = 0; i <= numOfRetries; i++) {
			String msg = listen();
			if (msg != null) {
				return msg;
			} else {
				synchronized (m_in) {
					try {
						m_in.wait(waitTime);
					} catch (InterruptedException ex) {
					}
				}
			}
		}
		return null;
	}

	@SuppressWarnings("unused")
	private String waitForAnswer() throws IOException {
		return waitForAnswer(100, 1);
	}

	public void sendMessage(String msg) {
		try {
			m_out.write(msg);
			m_out.write("\n");
			m_out.flush();
			fireInputMessage(msg);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

}

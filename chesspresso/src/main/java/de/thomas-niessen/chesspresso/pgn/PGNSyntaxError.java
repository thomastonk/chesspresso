/*******************************************************************************
 * Basic version: Copyright (C) 2003 Bernhard Seybold. All rights reserved.
 * All changes since then: Copyright (C) Thomas Niessen. All rights reserved.
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
package chesspresso.pgn;

/**
 * Represents PGN syntax errors (and warnings).
 *
 * @author Bernhard Seybold
 * 
 */
@SuppressWarnings("serial")
public class PGNSyntaxError extends java.lang.Exception {
	public final static int ERROR = 0, WARNING = 1, MESSAGE = 2;

	// ======================================================================

	private int m_severity;
	private String m_filename;
	private int m_lineNumber;
	private String m_lastToken;

	// ======================================================================

	public PGNSyntaxError(int severity, String msg, String filename, int lineNumber, String lastToken) {
		super(msg);
		m_severity = severity;
		m_filename = filename;
		m_lineNumber = lineNumber;
		m_lastToken = lastToken;
	}

	// ======================================================================

	public int getSeverity() {
		return m_severity;
	}

	public String getFilename() {
		return m_filename;
	}

	public int getLineNumber() {
		return m_lineNumber;
	}

	public String getLastToken() {
		return m_lastToken;
	}

	// ======================================================================

	public String toString() {
		if (m_severity == ERROR) {
			return "ERROR: " + m_filename + ":" + m_lineNumber + ": near " + m_lastToken + ": " + getMessage();
		} else if (m_severity == WARNING) {
			return "WARNING: " + m_filename + ":" + m_lineNumber + ": near " + m_lastToken + ": " + getMessage();
		} else if (m_severity == MESSAGE) {
			return "MESSAGE: " + m_filename + ":" + m_lineNumber + ": near " + m_lastToken + ": " + getMessage();
		} else {
			throw new RuntimeException("Illegal severity");
		}
	}

}

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
	public enum Severity {
		ERROR("ERROR"), WARNING("WARNING"), MESSAGE("MESSAGE");

		String desc;

		Severity(String desc) {
			this.desc = desc;
		}
	}

	// ======================================================================

	private final Severity m_severity;
	private final String m_filename;
	private final int m_lineNumber;
	private final String m_lastToken;

	// ======================================================================

	public PGNSyntaxError(Severity severity, String msg, String filename, int lineNumber, String lastToken) {
		super(msg);
		m_severity = severity;
		m_filename = filename;
		m_lineNumber = lineNumber;
		m_lastToken = lastToken;
	}

	// ======================================================================

	public Severity getSeverity() {
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

	@Override
	public String toString() {
		return m_severity.desc + ": " + m_filename + ":" + m_lineNumber + ": near " + m_lastToken + ": " + getMessage();
	}

}

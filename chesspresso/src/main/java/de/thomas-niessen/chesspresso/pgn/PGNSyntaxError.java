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

		final String desc;

		Severity(String desc) {
			this.desc = desc;
		}
	}

	// ======================================================================

	private final Severity severity;
	private final String filename;
	private final int lineNumber;
	private final String lastToken;

	// ======================================================================

	public PGNSyntaxError(Severity severity, String msg, String filename, int lineNumber, String lastToken) {
		super(msg);
		this.severity = severity;
		this.filename = filename;
		this.lineNumber = lineNumber;
		this.lastToken = lastToken;
	}

	// ======================================================================

	public Severity getSeverity() {
		return severity;
	}

	public String getFilename() {
		return filename;
	}

	public int getLineNumber() {
		return lineNumber;
	}

	public String getLastToken() {
		return lastToken;
	}

	// ======================================================================

	@Override
	public String toString() {
		return severity.desc + ": " + filename + ":" + lineNumber + ": near " + lastToken + ": " + getMessage();
	}

}

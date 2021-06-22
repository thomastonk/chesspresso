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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.swing.filechooser.FileFilter;

import chesspresso.Chess;
import chesspresso.Variant;
import chesspresso.game.Game;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;
import chesspresso.position.NAG;
import chesspresso.position.Position;

/**
 * Reader for PGN files.
 *
 * @author Bernhard Seybold
 * 
 */
public final class PGNReader extends PGN {

	public static boolean isPGNFile(String filename) {
		return filename != null && filename.toLowerCase().endsWith(".pgn");
	}

	public static boolean isPGNFileOrZipped(String filename) {
		if (filename != null) {
			filename = filename.toLowerCase();
			return filename.endsWith(".pgn") || filename.endsWith(".pgn.gz") || filename.endsWith(".zip");
		} else {
			return false;
		}
	}

	public static FileFilter getFileFilter() {
		return new FileFilter() {
			@Override
			public boolean accept(File file) {
				return file.isDirectory() || PGNReader.isPGNFileOrZipped(file.getName());
			}

			@Override
			public String getDescription() {
				return "PGN files (*.pgn, *.pgn.gz, *.zip)";
			}
		};
	}

	// ======================================================================

	private static final boolean DEBUG = false;

	private static final int MAX_TOKEN_SIZE = 8192;

	private static final boolean[] s_isToken;

	static {
		s_isToken = new boolean[128];
		Arrays.fill(s_isToken, false);

		for (int i = 0; i <= 32; i++)
			s_isToken[i] = true;

		s_isToken[TOK_ASTERISK] = true;
		s_isToken[TOK_COMMENT_BEGIN] = true;
		s_isToken[TOK_COMMENT_END] = true;
		s_isToken[TOK_LBRACKET] = true;
		s_isToken[TOK_RBRACKET] = true;
		s_isToken[TOK_LINE_BEGIN] = true;
		s_isToken[TOK_LINE_END] = true;
		s_isToken[TOK_NAG_BEGIN] = true;
		s_isToken[TOK_PERIOD] = true;
		s_isToken[TOK_QUOTE] = true;
		s_isToken[TOK_TAG_BEGIN] = true;
		s_isToken[TOK_TAG_END] = true;
		s_isToken['!'] = true; // direct NAGs
		s_isToken['?'] = true; // direct NAGs
	}

	// ======================================================================

	private LineNumberReader m_in;
	// 1.4 private CharBuffer m_charBuf;
	private String m_filename;

	private Game m_curGame;
	private int m_lastChar;
	private int m_lastToken;
	private boolean m_pushedBack;
	private char[] m_buf;
	private int m_lastTokenLength;
	private boolean m_ignoreLineComment;

	private PGNErrorHandler m_errorHandler;

	// ======================================================================

	public PGNReader(InputStream in, String name) {
		init();
		setInput(new InputStreamReader(in, StandardCharsets.ISO_8859_1), name);
		// TN: charset added; ISO-8859-1 according to PGN specification
	}

	// TN: was deprecated, but I think it is useful
	public PGNReader(Reader reader, String name) {
		init();
		setInput(reader, name);
	}

	// TN: was deprecated, but I think it is useful
	public PGNReader(String filename) throws IOException {
		init();
		if (filename.toLowerCase().endsWith(".gz")) {
			setInput(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename)), StandardCharsets.ISO_8859_1),
					filename);
			// TN: was: setInput(new InputStreamReader(new GZIPInputStream(new
			// FileInputStream(filename))), filename);
		} else {
			setInput(new InputStreamReader(new FileInputStream(filename), StandardCharsets.ISO_8859_1), filename);
			// TN: was: setInput(new FileReader(filename), filename);
		}
	}

	private void init() {
		m_buf = new char[MAX_TOKEN_SIZE];
		m_filename = null;
		m_errorHandler = null;
		m_pushedBack = false;
		m_lastToken = TOK_EOL;
		m_ignoreLineComment = false;
	}

	public void reset() throws FileNotFoundException {
		String fn = m_filename;
		init();
		InputStream iStrm = null;
		iStrm = new FileInputStream(fn);
		setInput(new InputStreamReader(iStrm, StandardCharsets.ISO_8859_1), fn);
	}

	// ======================================================================

	protected void setInput(Reader reader, String name) {
		if (reader instanceof LineNumberReader) {
			m_in = (LineNumberReader) reader;
		} else {
			m_in = new LineNumberReader(reader);
		}
		m_filename = name;
	}

	public void setErrorHandler(PGNErrorHandler handler) {
		m_errorHandler = handler;
	}

	// ======================================================================

	final static int TOK_EOF = -1, TOK_EOL = -2, TOK_IDENT = -3, TOK_STRING = -4, TOK_NO_TOKEN = -100;

	/**
	 * Returns the current line number. The first line is line 1, not line 0 as
	 * LineNumberReader.
	 *
	 * @return the current line number
	 */
	private int getLineNumber() {
		return m_in != null ? m_in.getLineNumber() + 1 : 0;
	}

	private String getLastTokenAsDebugString() {
		int last;
		last = getLastToken();
		if (last == TOK_EOF)
			return "EOF";
		if (last == TOK_EOL)
			return "EOL";
		if (last == TOK_NO_TOKEN)
			return "NO_TOKEN";
		if (last == TOK_IDENT)
			return getLastTokenAsString();
		if (last == TOK_COMMENT_BEGIN)
			return TOK_COMMENT_BEGIN + getLastTokenAsString() + TOK_COMMENT_END;
		if (last == TOK_STRING)
			return TOK_QUOTE + getLastTokenAsString() + TOK_QUOTE;
		return String.valueOf((char) last);
	}

	private void syntaxError(String msg) throws PGNSyntaxError {
		PGNSyntaxError error = new PGNSyntaxError(PGNSyntaxError.ERROR, msg, m_filename, getLineNumber(),
				getLastTokenAsDebugString());
		if (m_errorHandler != null)
			m_errorHandler.handleError(error);
		throw error;
	}

	private void warning(String msg) {
		if (m_errorHandler != null) {
			PGNSyntaxError warning = new PGNSyntaxError(PGNSyntaxError.WARNING, msg, m_filename, getLineNumber(),
					getLastTokenAsDebugString());
			m_errorHandler.handleWarning(warning);
		}
	}

	// ======================================================================

	private int get() throws IOException {
		return m_in.read();
	}

	private int getChar() throws IOException {
		if (m_pushedBack) {
			m_pushedBack = false;
			return m_lastChar;
		}
		int ch = get();
		while (ch == '\n' || ch == '\r' || ch == TOK_PGN_ESCAPE || (!m_ignoreLineComment && ch == TOK_LINE_COMMENT)) {
			while ((ch == '\n' || ch == '\r') && ch >= 0) {
				ch = get();
			}
			if (ch == TOK_PGN_ESCAPE && !m_ignoreLineComment) { // TN changed (here it is a workaround)
				do {
					ch = get();
				} while (ch != '\n' && ch != '\r' && ch >= 0);
			} else if (!m_ignoreLineComment && ch == TOK_LINE_COMMENT) { // TN changed
				do {
					ch = get();
				} while (ch != '\n' && ch != '\r' && ch >= 0);
			} else {
				m_pushedBack = true;
				m_lastChar = ch;
				return '\n';
			}
		}
		if (ch < 0)
			ch = TOK_EOF;
		m_lastChar = ch;
		return ch;
	}

	private int skipWhiteSpaces() throws IOException {
		int ch;
		do {
			ch = getChar();
		} while ((ch <= ' ' || ch == TOK_EOL) && ch >= 0);
		return ch;
	}

	private int getNextToken() throws PGNSyntaxError, IOException {
		m_lastTokenLength = 0;

		int ch = skipWhiteSpaces();
		if (ch == TOK_EOF) {
			m_lastToken = ch;
		} else if (ch == TOK_QUOTE) {
			for (;;) {
				ch = getChar();
				if (ch == TOK_QUOTE)
					break;
				if (ch < 0)
					syntaxError("Unfinished string");
				if (m_lastTokenLength >= MAX_TOKEN_SIZE)
					syntaxError("Token too long");
				m_buf[m_lastTokenLength++] = (char) ch;
			}
			m_lastToken = TOK_STRING;
		} else if (ch == TOK_COMMENT_BEGIN) {
			m_ignoreLineComment = true; // TN added
			int start = getLineNumber();
			for (;;) {
				ch = getChar();
				if (ch == TOK_COMMENT_END) {
					m_ignoreLineComment = false; // TN added
					break;
				}
				if (ch == TOK_EOF)
					syntaxError("Unfinished comment, started at line " + start);
				if (ch == '\n')
					ch = ' '; // end of line -> space
				if (m_lastTokenLength >= MAX_TOKEN_SIZE)
					syntaxError("Token too long");
				if (ch >= 0)
					m_buf[m_lastTokenLength++] = (char) ch;
			}
			m_lastToken = TOK_COMMENT_BEGIN;
		} else if (ch >= 0 && ch < s_isToken.length && s_isToken[ch]) {
			m_lastToken = ch;
		} else if (ch >= 0) {
			for (;;) {
				if (m_lastTokenLength >= MAX_TOKEN_SIZE)
					syntaxError("Token too long");
				m_buf[m_lastTokenLength++] = (char) ch;
				ch = getChar();
				if (ch < 0)
					break;
				if (ch < s_isToken.length && s_isToken[ch])
					break;
			}
			m_pushedBack = true;
			m_lastToken = TOK_IDENT;
		}
		return m_lastToken;
	}

	private int getLastToken() {
		return m_lastToken;
	}

	private boolean isLastTokenIdent() {
		return m_lastToken == -3;
	}

	private String getLastTokenAsString() {
		return String.valueOf(m_buf, 0, m_lastTokenLength);
	}

	private boolean isLastTokenInt() {
		for (int i = 0; i < m_lastTokenLength; i++) {
			int digit = m_buf[i];
			if (digit < '0' || digit > '9')
				return false; // =====>
		}
		return true;
	}

	private int getLastTokenAsInt() throws PGNSyntaxError {
		int value = 0;
		for (int i = 0; i < m_lastTokenLength; i++) {
			int digit = m_buf[i];
			if (digit < '0' || digit > '9')
				syntaxError("Not a digit " + digit);
			value = 10 * value + (m_buf[i] - 48);
		}
		return value;
	}

	// ======================================================================
	// routines for parsing header sections

	private void initForHeader() {
	}

	private boolean findNextGameStart() throws PGNSyntaxError, IOException {
		for (;;) {
			int last = getLastToken();
			if (last == TOK_EOF)
				return false;
			if (last == TOK_TAG_BEGIN)
				return true;
			getNextToken();
		}
	}

	private boolean parseTag() throws PGNSyntaxError, IOException {
		if (getLastToken() == TOK_TAG_BEGIN) {
			m_ignoreLineComment = true;
			String tagName = null, tagValue = null;

			if (getNextToken() == TOK_IDENT) {
				tagName = getLastTokenAsString();
			} else {
				syntaxError("Tag name expected");
			}

			if (getNextToken() != TOK_STRING) {
				syntaxError("Tag value expected");
			}
			tagValue = getLastTokenAsString();

			// compensate for quotes in tag values as produced e.g. by ChessBase
			while (getNextToken() != TOK_TAG_END) {
				tagValue = tagValue + " " + getLastTokenAsString();
			}

			m_curGame.setTag(tagName, tagValue);

			if (getLastToken() != TOK_TAG_END) {
				syntaxError(TOK_TAG_END + " expected");
			}
			m_ignoreLineComment = false;
			return true;
		} else {
			return false;
		}
	}

	private Variant getVariantFromTag() throws PGNSyntaxError {
		String variant = m_curGame.getTag(TAG_VARIANT);
		if (variant != null) {
			variant = variant.toLowerCase();
			if ((variant.contains("chess") && variant.contains("960"))
					|| (variant.contains("fischer") && variant.contains("random"))) {
				return Variant.CHESS960;
			} else if (variant.contains("standard") || variant.contains("three-check")) { // as in lichess
				return Variant.STANDARD;
			} else {
				throw new PGNSyntaxError(PGNSyntaxError.ERROR, "Unknown variant: " + m_curGame.getTag(TAG_VARIANT), m_filename,
						getLineNumber(), getLastTokenAsDebugString());
			}
		} else { // no variant tag
			return Variant.STANDARD;
		}
	}

	private void parseTagPairSection() throws PGNSyntaxError, IOException {
		findNextGameStart();
		while (parseTag()) {
			getNextToken();
		}
		if (getVariantFromTag() == Variant.CHESS960) { // Chess960 needs a FEN
			if (m_curGame.getTag(TAG_FEN) == null) {
				throw new PGNSyntaxError(PGNSyntaxError.ERROR, "Chess960 variant detected, but without FEN.", m_filename,
						getLineNumber(), "");
			}
		}
	}

	// ======================================================================
	// routines for parsing move text sections

	private void initForMovetext() throws PGNSyntaxError {
		if (getVariantFromTag() == Variant.CHESS960) {
			m_curGame.setVariant(Variant.CHESS960);
		}
		String fen = m_curGame.getTag(TAG_FEN);
		if (fen != null) {
			try {
				m_curGame.setGameByFEN(fen, false);
			} catch (Exception ex) {
				syntaxError(ex.getMessage());
			}
		}
	}

	private boolean isLastTokenResult() {
		return getLastTokenAsResult() != Chess.NO_RES;
	}

	private int getLastTokenAsResult() {
		// System.out.println("CheckResult: " + getLastTokenAsString());
		if (getLastToken() == TOK_ASTERISK)
			return Chess.RES_NOT_FINISHED;
		if (getLastToken() == TOK_EOF)
			return Chess.NO_RES;
		if (getLastToken() == TOK_COMMENT_BEGIN || getLastToken() == TOK_COMMENT_END)
			return Chess.NO_RES;
		if (m_buf[0] == '1') {
			if (m_buf[1] == '/') {
				if (m_lastTokenLength == 7 && m_buf[2] == '2' && m_buf[3] == '-' && m_buf[4] == '1' && m_buf[5] == '/'
						&& m_buf[6] == '2') {
					return Chess.RES_DRAW;
				}
			} else if (m_lastTokenLength == 3 && m_buf[1] == '-' && m_buf[2] == '0') {
				return Chess.RES_WHITE_WINS;
			}
		} else if (m_lastTokenLength == 3 && m_buf[0] == '0' && m_buf[1] == '-' && m_buf[2] == '1') {
			return Chess.RES_BLACK_WINS;
		}
		return Chess.NO_RES;
	}

	private boolean isLastTokenMoveNumber() {
		return isLastTokenInt();
	}

	@SuppressWarnings("unused")
	private boolean isLastTokenMoveNumber(int moveNumber) throws PGNSyntaxError {
		return isLastTokenMoveNumber() && getLastTokenAsInt() == moveNumber;
	}

	private short getLastTokenAsMove() throws PGNSyntaxError {
		if (DEBUG)
			System.out.println("getLastTokenAsMove " + getLastTokenAsString());

		if (!isLastTokenIdent())
			syntaxError("Move expected near ply " + m_curGame.getPosition().getPlyNumber());

		int next = 0;
		int last = m_lastTokenLength - 1;
		if (m_buf[last] == '+') {
			last--;
		} else if (m_buf[last] == '#') {
			last--;
		}

		// String s = getLastTokenAsString();
		// if (DEBUG) System.out.println("moveStr= " + s);
		short move = Move.ILLEGAL_MOVE;
		Variant variant = m_curGame.getPosition().getVariant();
		if (m_buf[0] == 'O' && m_buf[1] == '-' && m_buf[2] == 'O') {
			if (m_lastTokenLength >= 5 && m_buf[3] == '-' && m_buf[4] == 'O') {
				if (variant == Variant.STANDARD) {
					move = Move.getLongCastle(m_curGame.getPosition().getToPlay());
				} else {
					Position position = m_curGame.getPosition();
					move = Move.getChess960Castle(position.getToPlay(), position.getChess960KingFile(),
							position.getChess960QueensideRookFile());
				}
				next = 5;
			} else if (m_lastTokenLength >= 3) {
				if (variant == Variant.STANDARD) {
					move = Move.getShortCastle(m_curGame.getPosition().getToPlay());
				} else {
					Position position = m_curGame.getPosition();
					move = Move.getChess960Castle(position.getToPlay(), position.getChess960KingFile(),
							position.getChess960KingsideRookFile());
				}
				next = 3;
			} else {
				syntaxError("Illegal castle moven ear ply " + m_curGame.getPosition().getPlyNumber());
			}
		} else if (m_buf[0] == '0' && m_buf[1] == '-' && m_buf[2] == '0') {
			if (m_lastTokenLength >= 5 && m_buf[3] == '-' && m_buf[4] == '0') {
				warning("Castles with zeros");
				if (variant == Variant.STANDARD) {
					move = Move.getLongCastle(m_curGame.getPosition().getToPlay());
				} else {
					Position position = m_curGame.getPosition();
					move = Move.getChess960Castle(position.getToPlay(), position.getChess960KingFile(),
							position.getChess960QueensideRookFile());
				}
				next = 5;
			} else if (m_lastTokenLength >= 3) {
				warning("Castles with zeros");
				if (variant == Variant.STANDARD) {
					move = Move.getShortCastle(m_curGame.getPosition().getToPlay());
				} else {
					Position position = m_curGame.getPosition();
					move = Move.getChess960Castle(position.getToPlay(), position.getChess960KingFile(),
							position.getChess960QueensideRookFile());
				}
				next = 3;
			} else {
				syntaxError("Illegal castle move near ply " + m_curGame.getPosition().getPlyNumber());
			}
		} else if (m_buf[0] == '-' && m_buf[1] == '-') { // TN: null move code
			move = Move.getNullMove();
		} else if (m_buf[0] == 'Z' && m_buf[1] == '0') { // TN: null move code for CA
			move = Move.getNullMove();
		} else {
			char ch = m_buf[0];
			if (ch >= 'a' && ch <= 'h') {
				/*---------- pawn move ----------*/
				int col = Chess.NO_COL;
				if (1 > last)
					syntaxError("Illegal pawn move near ply " + m_curGame.getPosition().getPlyNumber() + ", move "
							+ getLastTokenAsString());
				if (m_buf[1] == 'x') {
					col = Chess.charToCol(ch);
					next = 2;
				}

				if (m_buf[1] >= 'a' && m_buf[1] <= 'h') { // the 'x' is missing!
					col = Chess.charToCol(ch);
					next = 1;
				}

				if (next + 1 > last)
					syntaxError("Illegal pawn move near ply " + m_curGame.getPosition().getPlyNumber()
							+ ", no destination square for move " + getLastTokenAsString());
				int toSqi = Chess.strToSqi(m_buf[next], m_buf[next + 1]);
				next += 2;

				int promo = Chess.NO_PIECE;
				if (next <= last && m_buf[next] == '=') {
					if (next < last) {
						promo = Chess.charToPiece(m_buf[next + 1]);
					} else {
						syntaxError("Illegal promotion move near ply " + m_curGame.getPosition().getPlyNumber()
								+ ", misssing piece for move " + getLastTokenAsString());
					}
				}
				move = m_curGame.getPosition().getPawnMove(col, toSqi, promo);
			} else {
				/*---------- non-pawn move ----------*/
				int piece = Chess.charToPiece(ch);

				if (last < 2) {
					syntaxError("Wrong move near ply " + m_curGame.getPosition().getPlyNumber()
							+ ", no destination square for move " + getLastTokenAsString());
				}
				int toSqi = Chess.strToSqi(m_buf[last - 1], m_buf[last]);
				last -= 2;

				if (m_buf[last] == 'x')
					last--; // capturing

				int row = Chess.NO_ROW, col = Chess.NO_COL;
				while (last >= 1) {
					char rowColChar = m_buf[last];
					int r = Chess.charToRow(rowColChar);
					if (r != Chess.NO_ROW) {
						row = r;
					} else {
						int c = Chess.charToCol(rowColChar);
						if (c != Chess.NO_COL) {
							col = c;
						} else {
							warning("Unknown char '" + rowColChar + "', row / column expected");
						}
					}
					last--;
				}
				move = m_curGame.getPosition().getPieceMove(piece, col, row, toSqi);
			}
		}
		if (DEBUG)
			System.out.println("  -> " + Move.getString(move));
		return move;
	}

	private static boolean isNAGStart(int ch) {
		return ch == TOK_NAG_BEGIN || ch == '!' || ch == '?';
	}

	private void parseNAG() throws PGNSyntaxError, IOException {
		// pre: NAG begin is current token
		// post: current Token is next after NAG
		if (getLastToken() == TOK_NAG_BEGIN) {
			getNextToken();
			if (isLastTokenInt()) {
				m_curGame.addNag((short) getLastTokenAsInt());
			} else {
				syntaxError("Illegal NAG: number expected");
			}
			getNextToken();
		} else if (getLastToken() == '!' || getLastToken() == '?') {
			StringBuilder nagSB = new StringBuilder();
			do {
				nagSB.append((char) getLastToken());
				getNextToken();
			} while (getLastToken() == '!' || getLastToken() == '?');
			try {
				short nag = NAG.ofString(nagSB.toString());
				warning("Direct NAG used " + nagSB.toString() + " -> $" + nag);
				m_curGame.addNag(nag);
			} catch (IllegalArgumentException ex) {
				syntaxError("Illegal direct NAG " + nagSB.toString());
			}
		} else {
			syntaxError("NAG begin expected");
		}
	}

	// TN: the old implementation respected move numbers
	private short parseHalfMove(String preMoveComment) throws PGNSyntaxError, IOException {
		short move = 1;
		if (isLastTokenMoveNumber()) {
			while (getNextToken() == TOK_PERIOD)
				;
		}
		try {
			move = getLastTokenAsMove();
			m_curGame.getPosition().doMove(move);
			if (preMoveComment != null && !preMoveComment.isEmpty()) {
				m_curGame.setPreMoveComment(preMoveComment);
			}
		} catch (IllegalMoveException ex) {
			syntaxError(ex.getMessage());
		}
		return move;
	}

	// TN: the old implementation respected move numbers, and was in this respect
	// more complicated.

	// TN: comments are a notorious difficult problem. PGN allows {}-comments almost
	// everywhere (except within tokens and as nested comments), but before and
	// behind moves, before the result not belonging to a move, multiple comments
	// etc.
	// Here we implement the Chessbase scheme: comments are pre- or post-move
	// comments, multiple comments will be put together in some ways, and comments
	// before the result are appended to the final move of the game.
	// (SCID's implementation is by far not as clever.)
	private void parseMovetextSection() throws PGNSyntaxError, IOException {
		int level = 0;
		boolean commentsArePreMove = true;
		List<String> comments = new ArrayList<>();
		while (!isLastTokenResult()) {
			boolean nextTokenNeeded = true;
			switch (getLastToken()) {
			case TOK_LINE_BEGIN -> {
				if (!comments.isEmpty()) {
					m_curGame.setPostMoveComment(aggregateComments(comments));
				}
				++level;
				commentsArePreMove = true;
				comments.clear();
				m_curGame.getPosition().undoMove();
				break;
			}
			case TOK_LINE_END -> {
				m_curGame.setPostMoveComment(aggregateComments(comments));
				--level;
				commentsArePreMove = true;
				comments.clear();
				if (level >= 0) {
					m_curGame.goBackToParentLine();
				} else {
					syntaxError("Unexpected variation end");
				}
				break;
			}
			case TOK_COMMENT_BEGIN -> {
				String comment = getLastTokenAsString().trim();
				if (!comment.isEmpty()) {
					comments.add(comment);
				}
				break;
			}
			default -> {
				if (isNAGStart(getLastToken())) {
					parseNAG();
					nextTokenNeeded = false;
				} else {
					if (commentsArePreMove) {
						if (comments.isEmpty()) {
							parseHalfMove(null);
						} else {
							parseHalfMove(aggregateComments(comments));
							comments.clear();
						}
					} else {
						String preMoveComment = null;
						if (comments.size() > 1) {
							preMoveComment = comments.get(comments.size() - 1);
							comments.remove(comments.size() - 1);
						}
						if (!comments.isEmpty()) {
							m_curGame.setPostMoveComment(aggregateComments(comments));
						}
						comments.clear();
						parseHalfMove(preMoveComment);
					}
					commentsArePreMove = false;
				}
				break;
			}
			}
			if (nextTokenNeeded) {
				getNextToken();
			}
		}

		// Remaining comments:
		if (!comments.isEmpty()) {
			if (m_curGame.getNumOfPlies() > 0) { // example: 1.d4 (1.e4) {A comment.} 1-0
				// the comments will be appended to the final move of the game.
				m_curGame.gotoStart();
				m_curGame.gotoEndOfLine();
				String postMoveComment = m_curGame.getPostMoveComment();
				String comment;
				if (postMoveComment != null) {
					comment = postMoveComment + " " + aggregateComments(comments);
				} else {
					comment = aggregateComments(comments);
				}
				m_curGame.setPostMoveComment(comment);
			} else { // example: {The moves are lost.} 1-0
				m_curGame.setEmptyGameComment(aggregateComments(comments));
			}
		}

		getLastTokenAsResult(); // check
		if (level != 0)
			syntaxError("Unfinished variations in game: " + level);
	}

	private String aggregateComments(Collection<String> comments) {
		StringBuilder aggregated = new StringBuilder();
		boolean first = true;
		for (String comment : comments) {
			if (first) {
				first = false;
			} else {
				aggregated.append(" ");
			}
			aggregated.append(comment);
		}
		return aggregated.toString();
	}

	// ======================================================================

	/**
	 * Returns the next PGN game.
	 *
	 * @return the next game
	 */
	public Game parseGame() throws PGNSyntaxError, IOException {
		return parseGame(new Game());
	}

	/**
	 * Parse the next PGN game into the argument and returns a reference.
	 *
	 * @return the next game
	 */
	public Game parseGame(Game game) throws PGNSyntaxError, IOException {
		if (DEBUG)
			System.out.println("===> new game");
		if (m_in == null)
			return null;
		if (game == null)
			return null;
		m_curGame = null;
		if (!findNextGameStart()) {
			return null;
		}
		m_curGame = game;
		m_curGame.setAlwaysAddLine(true);
		initForHeader();
		parseTagPairSection();
		initForMovetext();
		parseMovetextSection();
		m_curGame.pack();
		return m_curGame;
	}

}

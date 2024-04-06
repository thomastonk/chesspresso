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
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.swing.filechooser.FileFilter;

import chesspresso.Chess;
import chesspresso.Variant;
import chesspresso.game.Game;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;
import chesspresso.pgn.PGNSyntaxError.Severity;
import chesspresso.position.InvalidFenException;
import chesspresso.position.NAG;
import chesspresso.position.Position;

/**
 * Reader for PGN files.
 *
 * @author Bernhard Seybold
 * 
 */
public final class PGNReader {

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

	private static final boolean[] S_IS_TOKEN;

	static {
		S_IS_TOKEN = new boolean[128];
		Arrays.fill(S_IS_TOKEN, false);

		for (int i = 0; i <= 32; i++) {
			S_IS_TOKEN[i] = true;
		}

		S_IS_TOKEN[PGN.TOK_ASTERISK] = true;
		S_IS_TOKEN[PGN.TOK_COMMENT_BEGIN] = true;
		S_IS_TOKEN[PGN.TOK_COMMENT_END] = true;
		S_IS_TOKEN[PGN.TOK_LBRACKET] = true;
		S_IS_TOKEN[PGN.TOK_RBRACKET] = true;
		S_IS_TOKEN[PGN.TOK_LINE_BEGIN] = true;
		S_IS_TOKEN[PGN.TOK_LINE_END] = true;
		S_IS_TOKEN[PGN.TOK_NAG_BEGIN] = true;
		S_IS_TOKEN[PGN.TOK_PERIOD] = true;
		S_IS_TOKEN[PGN.TOK_QUOTE] = true;
		S_IS_TOKEN[PGN.TOK_TAG_BEGIN] = true;
		S_IS_TOKEN[PGN.TOK_TAG_END] = true;
		S_IS_TOKEN['!'] = true; // direct NAGs
		S_IS_TOKEN['?'] = true; // direct NAGs
	}

	// ======================================================================

	private LineNumberReader lineNumberReader;
	private String filename;

	private Game curGame;
	private int lastChar;
	private int lastToken;
	private boolean pushedBack;
	private char[] buf;
	private int lastTokenLength;
	private boolean ignoreLineComment;

	private PGNErrorHandler errorHandler;

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
		buf = new char[MAX_TOKEN_SIZE];
		filename = null;
		errorHandler = null;
		pushedBack = false;
		lastToken = TOK_EOL;
		ignoreLineComment = false;
	}

	public void reset() throws FileNotFoundException {
		String fn = filename;
		init();
		InputStream iStrm = new FileInputStream(fn);
		setInput(new InputStreamReader(iStrm, StandardCharsets.ISO_8859_1), fn);
	}

	// ======================================================================

	private void setInput(Reader reader, String name) {
		if (reader instanceof LineNumberReader) {
			lineNumberReader = (LineNumberReader) reader;
		} else {
			lineNumberReader = new LineNumberReader(reader);
		}
		filename = name;
	}

	public void setErrorHandler(PGNErrorHandler handler) {
		errorHandler = handler;
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
		return lineNumberReader != null ? lineNumberReader.getLineNumber() + 1 : 0;
	}

	private String getLastTokenAsDebugString() {
		int last;
		last = getLastToken();
		if (last == TOK_EOF) {
			return "EOF";
		}
		if (last == TOK_EOL) {
			return "EOL";
		}
		if (last == TOK_NO_TOKEN) {
			return "NO_TOKEN";
		}
		if (last == TOK_IDENT) {
			return getLastTokenAsString();
		}
		if (last == PGN.TOK_COMMENT_BEGIN) {
			return PGN.TOK_COMMENT_BEGIN + getLastTokenAsString() + PGN.TOK_COMMENT_END;
		}
		if (last == TOK_STRING) {
			return PGN.TOK_QUOTE + getLastTokenAsString() + PGN.TOK_QUOTE;
		}
		return String.valueOf((char) last);
	}

	private void syntaxError(String msg) throws PGNSyntaxError {
		PGNSyntaxError error = new PGNSyntaxError(Severity.ERROR, msg, filename, getLineNumber(), getLastTokenAsDebugString());
		if (errorHandler != null) {
			errorHandler.handleError(error);
		}
		throw error;
	}

	private void warning(String msg) {
		if (errorHandler != null) {
			PGNSyntaxError warning = new PGNSyntaxError(Severity.WARNING, msg, filename, getLineNumber(),
					getLastTokenAsDebugString());
			errorHandler.handleWarning(warning);
		}
	}

	// ======================================================================

	private int get() throws IOException {
		return lineNumberReader.read();
	}

	private int getChar() throws IOException {
		if (pushedBack) {
			pushedBack = false;
			return lastChar;
		}
		int ch = get();
		while (ch == '\n' || ch == '\r' || ch == PGN.TOK_PGN_ESCAPE || (!ignoreLineComment && ch == PGN.TOK_LINE_COMMENT)) {
			while ((ch == '\n' || ch == '\r') && ch >= 0) {
				ch = get();
			}
			if (ch == PGN.TOK_PGN_ESCAPE && !ignoreLineComment) { // TN changed (here it is a workaround)
				do {
					ch = get();
				} while (ch != '\n' && ch != '\r' && ch >= 0);
			} else if (!ignoreLineComment && ch == PGN.TOK_LINE_COMMENT) { // TN changed
				do {
					ch = get();
				} while (ch != '\n' && ch != '\r' && ch >= 0);
			} else {
				pushedBack = true;
				lastChar = ch;
				return '\n';
			}
		}
		if (ch < 0) {
			ch = TOK_EOF;
		}
		lastChar = ch;
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
		lastTokenLength = 0;

		int ch = skipWhiteSpaces();
		if (ch == TOK_EOF) {
			lastToken = ch;
		} else if (ch == PGN.TOK_QUOTE) {
			for (;;) {
				ch = getChar();
				if (ch == PGN.TOK_QUOTE) {
					break;
				}
				if (ch < 0) {
					syntaxError("Unfinished string");
				}
				if (lastTokenLength >= MAX_TOKEN_SIZE) {
					syntaxError("Token too long");
				}
				buf[lastTokenLength++] = (char) ch;
			}
			lastToken = TOK_STRING;
		} else if (ch == PGN.TOK_COMMENT_BEGIN) {
			ignoreLineComment = true; // TN added
			int start = getLineNumber();
			for (;;) {
				ch = getChar();
				if (ch == PGN.TOK_COMMENT_END) {
					ignoreLineComment = false; // TN added
					break;
				}
				if (ch == TOK_EOF) {
					syntaxError("Unfinished comment, started at line " + start);
				}
				if (ch == '\n') {
					ch = ' '; // end of line -> space
				}
				if (lastTokenLength >= MAX_TOKEN_SIZE) {
					syntaxError("Token too long");
				}
				if (ch >= 0) {
					buf[lastTokenLength++] = (char) ch;
				}
			}
			lastToken = PGN.TOK_COMMENT_BEGIN;
		} else if (ch >= 0 && ch < S_IS_TOKEN.length && S_IS_TOKEN[ch]) {
			lastToken = ch;
		} else if (ch >= 0) {
			do {
				if (lastTokenLength >= MAX_TOKEN_SIZE) {
					syntaxError("Token too long");
				}
				buf[lastTokenLength++] = (char) ch;
				ch = getChar();
			} while ((ch >= 0) && (ch >= S_IS_TOKEN.length || !S_IS_TOKEN[ch]));
			pushedBack = true;
			lastToken = TOK_IDENT;
		}
		return lastToken;
	}

	private int getLastToken() {
		return lastToken;
	}

	private boolean isLastTokenIdent() {
		return lastToken == -3;
	}

	private String getLastTokenAsString() {
		return String.valueOf(buf, 0, lastTokenLength);
	}

	private boolean isLastTokenInt() {
		for (int i = 0; i < lastTokenLength; i++) {
			int digit = buf[i];
			if (digit < '0' || digit > '9') {
				return false;
			}
		}
		return true;
	}

	private int getLastTokenAsInt() throws PGNSyntaxError {
		int value = 0;
		for (int i = 0; i < lastTokenLength; i++) {
			int digit = buf[i];
			if (digit < '0' || digit > '9') {
				syntaxError("Not a digit " + digit);
			}
			value = 10 * value + (buf[i] - 48);
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
			if (last == TOK_EOF) {
				return false;
			}
			if (last == PGN.TOK_TAG_BEGIN) {
				return true;
			}
			getNextToken();
		}
	}

	private boolean parseTag() throws PGNSyntaxError, IOException {
		if (getLastToken() == PGN.TOK_TAG_BEGIN) {
			ignoreLineComment = true;
			String tagName = null;

			if (getNextToken() == TOK_IDENT) {
				tagName = getLastTokenAsString();
			} else {
				syntaxError("Tag name expected");
			}

			if (getNextToken() != TOK_STRING) {
				syntaxError("Tag value expected");
			}
			StringBuilder tagValue = new StringBuilder(getLastTokenAsString());

			// compensate for quotes in tag values as produced e.g. by ChessBase
			while (getNextToken() != PGN.TOK_TAG_END) {
				tagValue.append(" ").append(getLastTokenAsString());
			}

			curGame.setTag(tagName, tagValue.toString());

			if (getLastToken() != PGN.TOK_TAG_END) {
				syntaxError(PGN.TOK_TAG_END + " expected");
			}
			ignoreLineComment = false;
			return true;
		} else {
			return false;
		}
	}

	private Variant getVariantFromTag() throws PGNSyntaxError {
		String variant = curGame.getTag(PGN.TAG_VARIANT);
		if (variant != null) {
			variant = variant.toLowerCase();
			if ((variant.contains("chess") && variant.contains("960"))
					|| (variant.contains("fischer") && variant.contains("random")) || variant.contains("freestyle")) {
				return Variant.CHESS960;
			} else if (variant.contains("standard") || variant.contains("three-check") || variant.contains("normal")
					|| variant.equals("chess")) {
				// lichess and tcec values 
				return Variant.STANDARD;
			} else {
				throw new PGNSyntaxError(Severity.ERROR, "Unknown variant: " + curGame.getTag(PGN.TAG_VARIANT), filename,
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
			if (curGame.getTag(PGN.TAG_FEN) == null) {
				throw new PGNSyntaxError(Severity.ERROR, "Chess960 variant detected, but without FEN.", filename, getLineNumber(),
						"");
			}
		}
		String fen = curGame.getTag(PGN.TAG_FEN);
		if (fen != null) { // test FEN string validity
			try {
				new Position(fen);
			} catch (InvalidFenException e) {
				throw new PGNSyntaxError(Severity.ERROR, e.getMessage(), filename, getLineNumber(), "");
			}
		}
	}

	// ======================================================================
	// routines for parsing move text sections

	private void initForMovetext() throws PGNSyntaxError {
		if (getVariantFromTag() == Variant.CHESS960) {
			curGame.setChess960();
		}
		String fen = curGame.getTag(PGN.TAG_FEN);
		if (fen != null) {
			try {
				curGame.setGameByFEN(fen, false);
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
		if (getLastToken() == PGN.TOK_ASTERISK) {
			return Chess.RES_NOT_FINISHED;
		}
		if ((getLastToken() == TOK_EOF) || getLastToken() == PGN.TOK_COMMENT_BEGIN || getLastToken() == PGN.TOK_COMMENT_END) {
			return Chess.NO_RES;
		}
		if (buf[0] == '1') {
			if (buf[1] == '/') {
				if (lastTokenLength == 7 && buf[2] == '2' && buf[3] == '-' && buf[4] == '1' && buf[5] == '/' && buf[6] == '2') {
					return Chess.RES_DRAW;
				}
			} else if (lastTokenLength == 3 && buf[1] == '-' && buf[2] == '0') {
				return Chess.RES_WHITE_WINS;
			}
		} else if (lastTokenLength == 3 && buf[0] == '0' && buf[1] == '-' && buf[2] == '1') {
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
		if (DEBUG) {
			System.out.println("getLastTokenAsMove " + getLastTokenAsString());
		}

		if (!isLastTokenIdent()) {
			syntaxError("Move expected near ply " + curGame.getPosition().getPlyNumber() + ", found '" + getLastTokenAsString()
					+ "'");
		}

		int next = 0;
		int last = lastTokenLength - 1;
		if (buf[last] == '+') {
			last--;
		} else if (buf[last] == '#') {
			last--;
		}

		// String s = getLastTokenAsString();
		// if (DEBUG) System.out.println("moveStr= " + s);
		short move = Move.ILLEGAL_MOVE;
		Variant variant = curGame.getPosition().getVariant();
		if (buf[0] == 'O' && buf[1] == '-' && buf[2] == 'O') {
			if (lastTokenLength >= 5 && buf[3] == '-' && buf[4] == 'O') {
				if (variant == Variant.STANDARD) {
					move = Move.getLongCastle(curGame.getPosition().getToPlay());
				} else {
					Position position = curGame.getPosition();
					move = Move.getChess960Castle(position.getToPlay(), position.getChess960KingFile(),
							position.getChess960QueensideRookFile());
				}
				next = 5;
			} else if (lastTokenLength >= 3) {
				if (variant == Variant.STANDARD) {
					move = Move.getShortCastle(curGame.getPosition().getToPlay());
				} else {
					Position position = curGame.getPosition();
					move = Move.getChess960Castle(position.getToPlay(), position.getChess960KingFile(),
							position.getChess960KingsideRookFile());
				}
				next = 3;
			} else {
				syntaxError("Illegal castle moven ear ply " + curGame.getPosition().getPlyNumber());
			}
		} else if (buf[0] == '0' && buf[1] == '-' && buf[2] == '0') {
			if (lastTokenLength >= 5 && buf[3] == '-' && buf[4] == '0') {
				warning("Castles with zeros");
				if (variant == Variant.STANDARD) {
					move = Move.getLongCastle(curGame.getPosition().getToPlay());
				} else {
					Position position = curGame.getPosition();
					move = Move.getChess960Castle(position.getToPlay(), position.getChess960KingFile(),
							position.getChess960QueensideRookFile());
				}
				next = 5;
			} else if (lastTokenLength >= 3) {
				warning("Castles with zeros");
				if (variant == Variant.STANDARD) {
					move = Move.getShortCastle(curGame.getPosition().getToPlay());
				} else {
					Position position = curGame.getPosition();
					move = Move.getChess960Castle(position.getToPlay(), position.getChess960KingFile(),
							position.getChess960QueensideRookFile());
				}
				next = 3;
			} else {
				syntaxError("Illegal castle move near ply " + curGame.getPosition().getPlyNumber());
			}
		} else if (buf[0] == '-' && buf[1] == '-') { // TN: null move code
			move = Move.getNullMove();
		} else if (buf[0] == 'Z' && buf[1] == '0') { // TN: null move code for CA
			move = Move.getNullMove();
		} else {
			char ch = buf[0];
			if (ch >= 'a' && ch <= 'h') {
				/*---------- pawn move ----------*/
				if (1 > last) {
					syntaxError("Illegal pawn move near ply " + curGame.getPosition().getPlyNumber() + ", move "
							+ getLastTokenAsString());
				}
				if (last >= 3 && (buf[1] >= '2' && buf[1] <= '7') && ((buf[2] >= 'a' && buf[2] <= 'h') || buf[2] == 'x')) { // LAN notation as used by Rusz's SEE, for example!
					int col = Chess.NO_COL;
					if (buf[2] == 'x') {
						col = Chess.charToCol(ch);
						next = 3;
					} else if (buf[2] != ch) { // the 'x' is missing!
						col = Chess.charToCol(ch);
						next = 2;
					} else {
						next = 2;
					}
					if (next + 1 > last) {
						syntaxError("Illegal pawn move near ply " + curGame.getPosition().getPlyNumber()
								+ ", no destination square for move " + getLastTokenAsString());
					}
					int toSqi = Chess.strToSqi(buf[next], buf[next + 1]);
					next += 2;

					int promo = Chess.NO_PIECE;
					if (next <= last && buf[next] == '=') {
						if (next < last) {
							promo = Chess.charToPiece(buf[next + 1]);
						} else {
							syntaxError("Illegal promotion move near ply " + curGame.getPosition().getPlyNumber()
									+ ", misssing piece for move " + getLastTokenAsString());
						}
					}
					move = curGame.getPosition().getPawnMove(col, toSqi, promo);
				} else { // standard-compliant encoding!
					int col = Chess.NO_COL;
					if (buf[1] == 'x') {
						col = Chess.charToCol(ch);
						next = 2;
					}

					if (buf[1] >= 'a' && buf[1] <= 'h') { // the 'x' is missing!
						col = Chess.charToCol(ch);
						next = 1;
					}

					if (next + 1 > last) {
						syntaxError("Illegal pawn move near ply " + curGame.getPosition().getPlyNumber()
								+ ", no destination square for move " + getLastTokenAsString());
					}
					int toSqi = Chess.strToSqi(buf[next], buf[next + 1]);
					next += 2;

					int promo = Chess.NO_PIECE;
					if (next <= last && buf[next] == '=') {
						if (next < last) {
							promo = Chess.charToPiece(buf[next + 1]);
						} else {
							syntaxError("Illegal promotion move near ply " + curGame.getPosition().getPlyNumber()
									+ ", misssing piece for move " + getLastTokenAsString());
						}
					}
					move = curGame.getPosition().getPawnMove(col, toSqi, promo);
				}

			} else {
				/*---------- non-pawn move ----------*/
				int piece = Chess.charToPiece(ch);

				if (last < 2) {
					syntaxError("Wrong move near ply " + curGame.getPosition().getPlyNumber()
							+ ", no destination square for move " + getLastTokenAsString());
				}
				int toSqi = Chess.strToSqi(buf[last - 1], buf[last]);
				last -= 2;

				if (buf[last] == 'x') {
					last--; // capturing
				}

				int row = Chess.NO_ROW, col = Chess.NO_COL;
				while (last >= 1) {
					char rowColChar = buf[last];
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
				move = curGame.getPosition().getPieceMove(piece, col, row, toSqi);
			}
		}
		if (DEBUG) {
			System.out.println("  -> " + Move.getString(move));
		}
		return move;
	}

	private static boolean isNAGStart(int ch) {
		return ch == PGN.TOK_NAG_BEGIN || ch == '!' || ch == '?';
	}

	private void parseNAG() throws PGNSyntaxError, IOException {
		// pre: NAG begin is current token
		// post: current Token is next after NAG
		if (getLastToken() == PGN.TOK_NAG_BEGIN) {
			getNextToken();
			if (isLastTokenInt()) {
				curGame.addNag((short) getLastTokenAsInt());
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
			short nag = NAG.ofString(nagSB.toString());
			if (nag != -1) {
				warning("Direct NAG used " + nagSB.toString() + " -> $" + nag);
				curGame.addNag(nag);
			} else {
				syntaxError("Illegal direct NAG " + nagSB.toString());
			}
		} else {
			syntaxError("NAG begin expected");
		}
	}

	// TN: the old implementation respected move numbers
	private short parseHalfMove(String preMoveComment) throws PGNSyntaxError, IOException {
		short move = Move.ILLEGAL_MOVE;
		if (isLastTokenMoveNumber()) {
			while (getNextToken() == PGN.TOK_PERIOD) {
			}
		}
		if (!isLastTokenResult()) {
			try {
				move = getLastTokenAsMove();
				curGame.getPosition().doMove(move);
				if (preMoveComment != null && !preMoveComment.isEmpty()) {
					curGame.setPreMoveComment(preMoveComment);
				}
			} catch (IllegalMoveException ex) {
				syntaxError(ex.getMessage());
			}
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
			case PGN.TOK_LINE_BEGIN -> {
				if (!comments.isEmpty()) {
					curGame.setPostMoveComment(aggregateComments(comments));
				}
				++level;
				commentsArePreMove = true;
				comments.clear();
				curGame.getPosition().undoMove();
				break;
			}
			case PGN.TOK_LINE_END -> {
				if (!comments.isEmpty()) {
					curGame.setPostMoveComment(aggregateComments(comments));
				}
				--level;
				commentsArePreMove = true;
				comments.clear();
				if (level >= 0) {
					curGame.goBackToParentLine();
				} else {
					syntaxError("Unexpected variation end");
				}
				break;
			}
			case PGN.TOK_COMMENT_BEGIN -> {
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
							if (parseHalfMove(null) == Move.ILLEGAL_MOVE) {
								nextTokenNeeded = false;
							}
						} else {
							if (parseHalfMove(aggregateComments(comments)) == Move.ILLEGAL_MOVE) {
								nextTokenNeeded = false;
							}
							comments.clear();
						}
					} else {
						String preMoveComment = null;
						if (comments.size() > 1) {
							preMoveComment = comments.get(comments.size() - 1);
							comments.remove(comments.size() - 1);
						}
						if (!comments.isEmpty()) {
							curGame.setPostMoveComment(aggregateComments(comments));
						}
						comments.clear();
						if (parseHalfMove(preMoveComment) == Move.ILLEGAL_MOVE) {
							nextTokenNeeded = false;
						}
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
			if (curGame.getNumOfPlies() > 0) { // example: 1.d4 (1.e4) {A comment.} 1-0
				// the comments will be appended to the final move of the game.
				curGame.gotoStart();
				curGame.gotoEndOfLine();
				String postMoveComment = curGame.getPostMoveComment();
				String comment;
				if (postMoveComment != null) {
					comment = postMoveComment + " " + aggregateComments(comments);
				} else {
					comment = aggregateComments(comments);
				}
				curGame.setPostMoveComment(comment);
			} else { // example: {The moves are lost.} 1-0
				curGame.setEmptyGameComment(aggregateComments(comments));
			}
		}

		getLastTokenAsResult(); // check
		if (level != 0) {
			syntaxError("Unfinished variations in game: " + level);
		}
	}

	private String aggregateComments(Iterable<String> comments) {
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
	 * Parses the next PGN game into the argument and returns a reference.
	 *
	 * @return the next game
	 */
	public Game parseGame(Game game) throws PGNSyntaxError, IOException {
		if (DEBUG) {
			System.out.println("===> new game");
		}
		if ((lineNumberReader == null) || (game == null)) {
			return null;
		}
		curGame = null;
		if (!findNextGameStart()) {
			return null;
		}
		curGame = game;
		curGame.setAlwaysAddLine(true);
		initForHeader();
		parseTagPairSection();
		initForMovetext();
		parseMovetextSection();
		curGame.pack();
		curGame.setAlwaysAddLine(false);
		return curGame;
	}

}

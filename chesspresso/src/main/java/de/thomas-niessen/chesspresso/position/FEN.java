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
package chesspresso.position;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import chesspresso.Chess;
import chesspresso.Variant;
import chesspresso.position.ImmutablePosition.Validity;

public class FEN {

	private static final char[] fenChars = { 'K', 'P', 'Q', 'R', 'B', 'N', '-', 'n', 'b', 'r', 'q', 'p', 'k' };

	public static int fenCharToStone(char ch) {
		for (int stone = Chess.MIN_STONE; stone <= Chess.MAX_STONE; stone++) {
			if (fenChars[stone - Chess.MIN_STONE] == ch) {
				return stone;
			}
		}
		return Chess.NO_STONE;
	}

	public static char stoneToFenChar(int stone) {
		if (stone >= Chess.MIN_STONE && stone <= Chess.MAX_STONE) {
			return fenChars[stone - Chess.MIN_STONE];
		} else {
			return '?';
		}
	}

	public static final String START_POSITION = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
	public static final String EMPTY_BOARD = "8/8/8/8/8/8/8/8 w - - 0 1";

	private static final Pattern SQUARE_PATTERN = Pattern.compile("[a-h][1-8]");
	private static final Pattern DOUBLE_WHITE_KING_PATTERN = Pattern.compile(".*K.*K.*");
	private static final Pattern DOUBLE_BLACK_KING_PATTERN = Pattern.compile(".*k.*k.*");

	private static final Pattern ANY_CASTLING_PATTERN = Pattern.compile("[a-hA-HkqKQ]+");
	private static final Pattern CLASSICAL_CASTLING_PATTERN = Pattern.compile("[KQkq]+");
	private static final Pattern SHREDDER_CASTLING_PATTERN = Pattern.compile("[A-Ha-h]+");

	static void initFromFEN(MutablePosition pos, String fen, boolean validate) throws InvalidFenException {
		pos.clear();

		if (fen == null) {
			throw new InvalidFenException("Invalid FEN: FEN string is null.");
		}
		if (fen.equals(FEN.START_POSITION)) { // a little bit of optimization
			initFromStandardStartFEN(pos);
			return;
		}

		String[] fenParts = fen.trim().split(" +");
		if (fenParts.length == 0) {
			throw new InvalidFenException("Invalid FEN: empty string or only white spaces.");
		}

		if (validate) { // double kings of the same color will found nowhere else
			if (DOUBLE_WHITE_KING_PATTERN.matcher(fenParts[0]).matches()) {
				throw new InvalidFenException("Invalid FEN: two or more white kings.");
			}
			if (DOUBLE_BLACK_KING_PATTERN.matcher(fenParts[0]).matches()) {
				throw new InvalidFenException("Invalid FEN: two or more black kings.");
			}
		}

		/* ========== 1st field : pieces ========== */
		String[] rows = fenParts[0].split("/");
		if (rows.length != 8) {
			String msg;
			if (rows.length == 1) {
				msg = "Invalid FEN: invalid piece description, only " + rows.length + " row found.";
			} else if (rows.length < 8) {
				msg = "Invalid FEN: invalid piece description, only " + rows.length + " row(s) found.";
			} else {
				msg = "Invalid FEN: invalid piece description, " + rows.length + " row(s) found.";
			}
			throw new InvalidFenException(msg);
		}
		for (int rowIndex = 0; rowIndex < 8; ++rowIndex) {
			char ch;
			int colIndex = 0;
			String row = rows[rowIndex];
			for (int index = 0; index < row.length(); ++index) {
				ch = row.charAt(index);
				if (ch >= '1' && ch <= '8') {
					int num = ch - '0';
					if (colIndex + num > 8) {
						throw new InvalidFenException("Invalid FEN: too many pieces in row " + (rowIndex + 1));
					}
					for (int j = 0; j < num; ++j) {
						pos.setStone(Chess.coorToSqi(colIndex, 7 - rowIndex), Chess.NO_STONE);
						++colIndex;
					}
				} else {
					int stone = FEN.fenCharToStone(ch);
					if (stone == Chess.NO_STONE) {
						throw new InvalidFenException("Invalid FEN: illegal piece char: " + ch);
					}
					pos.setStone(Chess.coorToSqi(colIndex, 7 - rowIndex), stone);
					++colIndex;
				}
			}
			if (colIndex != 8) {
				throw new InvalidFenException("Invalid FEN: check information '" + row + "' for FEN row " + (rowIndex + 1)
						+ " (board row " + (8 - rowIndex) + ")");
			}
		}

		/* ========== 2nd field : to play ========== */
		if (fenParts.length > 1) {
			String toPlay = fenParts[1].toLowerCase();
			if (toPlay.equals("w")) {
				pos.setToPlay(Chess.WHITE);
			} else if (toPlay.equals("b")) {
				pos.setToPlay(Chess.BLACK);
			} else {
				throw new InvalidFenException("Invalid FEN: expected 'w' or 'b' as second field, but found " + fenParts[1]);
			}
		} else { // default value
			pos.setToPlay(Chess.WHITE);
		}

		/* ========== 3rd field : castles ========== */
		if (fenParts.length > 2) {
			String castleString = fenParts[2];
			if (!castleString.equals("-")) {
				if (castleString.length() > 4) {
					throw new InvalidFenException(
							"Invalid FEN: expected castling information of length at most 4, found '" + castleString + "'");
				}
				if (!ANY_CASTLING_PATTERN.matcher(castleString).matches()) {
					throw new InvalidFenException("Invalid FEN: illegal letter found  in '" + castleString + "'");
				}
				CastlingInfoError error = setCastlingAndVariant(pos, castleString);
				if (error != CastlingInfoError.PROCESSED) {
					throw new InvalidFenException("Invalid FEN: " + error.getText() + ".");
				}
			}
		} else { // determine castling possibilities from position
			int castles = ImmutablePosition.NO_CASTLES;
			if (Chess.WHITE_KING == pos.getStone(Chess.E1)) {
				if (Chess.WHITE_ROOK == pos.getStone(Chess.H1)) {
					castles |= ImmutablePosition.WHITE_SHORT_CASTLE;
				}
				if (Chess.WHITE_ROOK == pos.getStone(Chess.A1)) {
					castles |= ImmutablePosition.WHITE_LONG_CASTLE;
				}
			}
			if (Chess.BLACK_KING == pos.getStone(Chess.E8)) {
				if (Chess.BLACK_ROOK == pos.getStone(Chess.H8)) {
					castles |= ImmutablePosition.BLACK_SHORT_CASTLE;
				}
				if (Chess.BLACK_ROOK == pos.getStone(Chess.A8)) {
					castles |= ImmutablePosition.BLACK_LONG_CASTLE;
				}
			}
			pos.setCastles(castles);
		}

		/* ========== 4th field : ep square ========== */
		if (fenParts.length > 3) {
			String epSquare = fenParts[3];
			if (epSquare.equals("-")) {
				pos.setSqiEP(Chess.NO_SQUARE);
			} else {
				if (SQUARE_PATTERN.matcher(epSquare).matches()) {
					pos.setSqiEP(Chess.strToSqi(epSquare));
				} else {
					throw new InvalidFenException("Invalid FEN: expected en passant square, found " + epSquare);
				}
			}
		} else { // default value
			pos.setSqiEP(Chess.NO_SQUARE);
		}

		/* ========== 5th field : half move clock ========== */
		if (fenParts.length > 4) {
			String hmClock = fenParts[4];
			try {
				pos.setHalfMoveClock(Integer.parseInt(hmClock));
			} catch (NumberFormatException e) {
				throw new InvalidFenException("Invalid FEN: tried to evaluate the half-move clock, found " + hmClock);
			}
		} else { // default value
			pos.setHalfMoveClock(0);
		}

		/* ========== 6th field : full move number ========== */
		if (fenParts.length > 5) {
			int moveNumber;
			try {
				moveNumber = Integer.parseInt(fenParts[5]);
			} catch (NumberFormatException e) {
				throw new InvalidFenException("Invalid FEN: tried to evaluate the move number, found " + fenParts[5]);
			}
			if (moveNumber < 0) {
				throw new InvalidFenException("Invalid FEN: tried to evaluate the move number, found " + fenParts[5]);
			}
			if (moveNumber == 0) {
				moveNumber = 1;
			}
			int ply;
			if (pos.getToPlay() == Chess.WHITE) {
				ply = 2 * (moveNumber - 1);
			} else {
				ply = 2 * (moveNumber - 1) + 1;
			}
			pos.setPlyOffset(ply);
		} else { // default value
			int ply = pos.getToPlay() == Chess.WHITE ? 0 : 1;
			pos.setPlyOffset(ply);
		}

		/* ============= check the resulting position ========== */
		if (validate) {
			if (pos.getValidity() != Validity.IS_VALID) {
				throw new InvalidFenException("Invalid FEN: " + pos.getValidity());
			}
			try {
				pos.internalValidate();
			} catch (Exception e) {
				e.printStackTrace();
				throw new InvalidFenException("Invalid FEN: " + e.getMessage());
			}
		}
	}

	private static void initFromStandardStartFEN(MutablePosition pos) {
		pos.setStone(Chess.A1, Chess.WHITE_ROOK);
		pos.setStone(Chess.B1, Chess.WHITE_KNIGHT);
		pos.setStone(Chess.C1, Chess.WHITE_BISHOP);
		pos.setStone(Chess.D1, Chess.WHITE_QUEEN);
		pos.setStone(Chess.E1, Chess.WHITE_KING);
		pos.setStone(Chess.F1, Chess.WHITE_BISHOP);
		pos.setStone(Chess.G1, Chess.WHITE_KNIGHT);
		pos.setStone(Chess.H1, Chess.WHITE_ROOK);
		pos.setStone(Chess.A8, Chess.BLACK_ROOK);
		pos.setStone(Chess.B8, Chess.BLACK_KNIGHT);
		pos.setStone(Chess.C8, Chess.BLACK_BISHOP);
		pos.setStone(Chess.D8, Chess.BLACK_QUEEN);
		pos.setStone(Chess.E8, Chess.BLACK_KING);
		pos.setStone(Chess.F8, Chess.BLACK_BISHOP);
		pos.setStone(Chess.G8, Chess.BLACK_KNIGHT);
		pos.setStone(Chess.H8, Chess.BLACK_ROOK);
		for (int i = Chess.A2; i <= Chess.H2; ++i) {
			pos.setStone(i, Chess.WHITE_PAWN);
		}
		for (int i = Chess.A7; i <= Chess.H7; ++i) {
			pos.setStone(i, Chess.BLACK_PAWN);
		}
		pos.setToPlay(Chess.WHITE);
		pos.setCastles(ImmutablePosition.ALL_CASTLES);
		pos.setSqiEP(Chess.NO_SQUARE);
		pos.setHalfMoveClock(0);
		pos.setPlyOffset(0);
	}

	public static enum CastlingInfoError {
		PROCESSED("Processed without error"), NOT_PROCESSED("Not processed"), ILLEGAL_CHARACTER("Illegal character"),
		ILLEGAL_WHITE_KING_SQUARE("Illegal white king square"), ILLEGAL_BLACK_KING_SQUARE("Illegal black king square"),
		REPEATED_INFORMATION("Repeated information"),
		KINGS_ON_DIFFERENT_FILES("Both sides can castle, but kings are on different files"),
		QUEENSIDE_ROOKS_ON_DIFFERENT_FILES("Both sides can castle queenside, but rooks on different squares"),
		KINGSIDE_ROOKS_ON_DIFFERENT_FILES("Both sides can castle kingside, but rooks on different squares"),
		NO_WHITE_ROOK_ON_A1("No white rook on a1"), NO_WHITE_ROOK_ON_B1("No white rook on b1"),
		NO_WHITE_ROOK_ON_C1("No white rook on c1"), NO_WHITE_ROOK_ON_D1("No white rook on d1"),
		NO_WHITE_ROOK_ON_E1("No white rook on e1"), NO_WHITE_ROOK_ON_F1("No white rook on f1"),
		NO_WHITE_ROOK_ON_G1("No white rook on g1"), NO_WHITE_ROOK_ON_H1("No white rook on h1"),
		NO_BLACK_ROOK_ON_A8("No black rook on a8"), NO_BLACK_ROOK_ON_B8("No black rook on b8"),
		NO_BLACK_ROOK_ON_C8("No black rook on c8"), NO_BLACK_ROOK_ON_D8("No black rook on d8"),
		NO_BLACK_ROOK_ON_E8("No black rook on e8"), NO_BLACK_ROOK_ON_F8("No black rook on f8"),
		NO_BLACK_ROOK_ON_G8("No black rook on g8"), NO_BLACK_ROOK_ON_H8("No black rook on h8"),
		NO_WHITE_KINGSIDE_ROOK("No white kingside rook"), NO_WHITE_QUEENSIDE_ROOK("No white queenside rook"),
		NO_BLACK_KINGSIDE_ROOK("No black kingside rook"), NO_BLACK_QUEENSIDE_ROOK("No black queenside rook"),
		INVALID_CASTLING_INFORMATION("Invalid castling information");

		private final String text;

		CastlingInfoError(String text) {
			this.text = text;
		}

		public String getText() {
			return text;
		}
	}

	private static CastlingInfoError setCastlingAndVariant(MutablePosition pos, String castleString) {
		// Pre-condition: The castleString matches '[a-hkqA-HKQ]+' and has at most four letters.
		if (CLASSICAL_CASTLING_PATTERN.matcher(castleString).matches()) {
			CastlingInfoError error = checkAndSetStandardCastling(pos, castleString);
			if (error == CastlingInfoError.PROCESSED) {
				return CastlingInfoError.PROCESSED;
			}
			if (error != CastlingInfoError.NOT_PROCESSED) {
				String msg = "Internal error: Unexpected return value of FEN::checkAndSetStandardCastling for '" + pos.getEpFEN()
						+ "' and '" + castleString + "'";
				throw new RuntimeException(msg);
			}
		}
		if (!SHREDDER_CASTLING_PATTERN.matcher(castleString).matches()) {
			String[] cSArray = new String[] { castleString };
			CastlingInfoError error = xFenToShredderFen(pos, cSArray);
			if (error != CastlingInfoError.PROCESSED) {
				return error;
			}
			castleString = cSArray[0];
		}
		return checkAndSetChess960CastlingShredder(pos, castleString);
	}

	private static CastlingInfoError xFenToShredderFen(MutablePosition pos, String[] cSArray) {
		// Pre-condition: castleString matches '[a-hkqA-HKQ]+', but not "[A-Ha-h]+".
		String castleString = cSArray[0];
		if (castleString.contains("K")) {
			int whiteKingSquare = pos.getWhitesKingSquare();
			if (whiteKingSquare >= Chess.H1 || whiteKingSquare <= Chess.A1) {
				return CastlingInfoError.ILLEGAL_WHITE_KING_SQUARE;
			}
			char ch = 'H';
			boolean rookFound = false;
			for (int square = Chess.H1; square > whiteKingSquare; --square, --ch) {
				if (pos.getStone(square) == Chess.WHITE_ROOK) {
					castleString = castleString.replace('K', ch);
					rookFound = true;
					break;
				}
			}
			if (!rookFound) {
				return CastlingInfoError.NO_WHITE_KINGSIDE_ROOK;
			}
			if (castleString.contains("K")) {
				return CastlingInfoError.REPEATED_INFORMATION;
			}
		}
		if (castleString.contains("Q")) {
			int whiteKingSquare = pos.getWhitesKingSquare();
			if (whiteKingSquare >= Chess.H1 || whiteKingSquare <= Chess.A1) {
				return CastlingInfoError.ILLEGAL_WHITE_KING_SQUARE;
			}
			char ch = 'A';
			boolean rookFound = false;
			for (int square = Chess.A1; square < whiteKingSquare; ++square, ++ch) {
				if (pos.getStone(square) == Chess.WHITE_ROOK) {
					castleString = castleString.replace('Q', ch);
					rookFound = true;
					break;
				}
			}
			if (!rookFound) {
				return CastlingInfoError.NO_WHITE_QUEENSIDE_ROOK;
			}
			if (castleString.contains("Q")) {
				return CastlingInfoError.REPEATED_INFORMATION;
			}
		}
		if (castleString.contains("k")) {
			int blackKingSquare = pos.getBlacksKingSquare();
			if (blackKingSquare >= Chess.H8 || blackKingSquare <= Chess.A8) {
				return CastlingInfoError.ILLEGAL_BLACK_KING_SQUARE;
			}
			char ch = 'h';
			boolean rookFound = false;
			for (int square = Chess.H8; square > blackKingSquare; --square, --ch) {
				if (pos.getStone(square) == Chess.BLACK_ROOK) {
					castleString = castleString.replace('k', ch);
					rookFound = true;
					break;
				}
			}
			if (!rookFound) {
				return CastlingInfoError.NO_BLACK_KINGSIDE_ROOK;
			}
			if (castleString.contains("k")) {
				return CastlingInfoError.REPEATED_INFORMATION;
			}
		}
		if (castleString.contains("q")) {
			int blackKingSquare = pos.getBlacksKingSquare();
			if (blackKingSquare >= Chess.H8 || blackKingSquare <= Chess.A8) {
				return CastlingInfoError.ILLEGAL_BLACK_KING_SQUARE;
			}
			char ch = 'a';
			boolean rookFound = false;
			for (int square = Chess.A8; square < blackKingSquare; ++square, ++ch) {
				if (pos.getStone(square) == Chess.BLACK_ROOK) {
					castleString = castleString.replace('q', ch);
					rookFound = true;
					break;
				}
			}
			if (!rookFound) {
				return CastlingInfoError.NO_BLACK_QUEENSIDE_ROOK;
			}
			if (castleString.contains("q")) {
				return CastlingInfoError.REPEATED_INFORMATION;
			}
		}
		cSArray[0] = castleString;
		return CastlingInfoError.PROCESSED;
	}

	private static CastlingInfoError checkAndSetStandardCastling(MutablePosition pos, String castleString) {
		// Pre-condition: casteString matches '[KQkq]+'.
		boolean whiteCanCastleKingSide = false;
		boolean whiteCanCastleQueenSide = false;
		boolean blackCanCastleKingSide = false;
		boolean blackCanCastleQueenSide = false;
		for (int i = 0; i < castleString.length(); ++i) {
			char ch = castleString.charAt(i);
			if (ch == 'K') {
				whiteCanCastleKingSide = true;
			} else if (ch == 'Q') {
				whiteCanCastleQueenSide = true;
			} else if (ch == 'k') {
				blackCanCastleKingSide = true;
			} else if (ch == 'q') {
				blackCanCastleQueenSide = true;
			}
		}
		if (whiteCanCastleKingSide || whiteCanCastleQueenSide) {
			if (pos.getWhitesKingSquare() != Chess.E1) {
				return CastlingInfoError.NOT_PROCESSED;
			}
		}
		if (blackCanCastleKingSide || blackCanCastleQueenSide) {
			if (pos.getBlacksKingSquare() != Chess.E8) {
				return CastlingInfoError.NOT_PROCESSED;
			}
		}
		int castles = ImmutablePosition.NO_CASTLES;
		if (whiteCanCastleKingSide) {
			if (pos.getStone(Chess.H1) != Chess.WHITE_ROOK) {
				return CastlingInfoError.NOT_PROCESSED;
			}
			castles |= ImmutablePosition.WHITE_SHORT_CASTLE;
		}
		if (whiteCanCastleQueenSide) {
			if (pos.getStone(Chess.A1) != Chess.WHITE_ROOK) {
				return CastlingInfoError.NOT_PROCESSED;
			}
			castles |= ImmutablePosition.WHITE_LONG_CASTLE;
		}
		if (blackCanCastleKingSide) {
			if (pos.getStone(Chess.H8) != Chess.BLACK_ROOK) {
				return CastlingInfoError.NOT_PROCESSED;
			}
			castles |= ImmutablePosition.BLACK_SHORT_CASTLE;
		}
		if (blackCanCastleQueenSide) {
			if (pos.getStone(Chess.A8) != Chess.BLACK_ROOK) {
				return CastlingInfoError.NOT_PROCESSED;
			}
			castles |= ImmutablePosition.BLACK_LONG_CASTLE;
		}
		pos.setCastles(castles);
		return CastlingInfoError.PROCESSED;
	}

	private static CastlingInfoError checkAndSetChess960CastlingShredder(MutablePosition pos, String castleString) {
		if (!SHREDDER_CASTLING_PATTERN.matcher(castleString).matches()) {
			return CastlingInfoError.ILLEGAL_CHARACTER;
		}

		boolean whiteCanCastle = castleString.matches(".*[A-H].*");
		boolean blackCanCastle = castleString.matches(".*[a-h].*");

		// start with the kings
		int whitesKingSquare = pos.getWhitesKingSquare();
		int blacksKingSquare = pos.getBlacksKingSquare();

		if (whiteCanCastle) { // White's king square is relevant
			if (whitesKingSquare <= Chess.A1 || whitesKingSquare >= Chess.H1) {
				return CastlingInfoError.ILLEGAL_WHITE_KING_SQUARE;
			}
		}
		if (blackCanCastle) { // Black's king square is relevant
			if (blacksKingSquare <= Chess.A8 || blacksKingSquare >= Chess.H8) {
				return CastlingInfoError.ILLEGAL_BLACK_KING_SQUARE;
			}
		}

		if (whiteCanCastle && blackCanCastle) {
			if (whitesKingSquare % 8 != blacksKingSquare % 8) {
				return CastlingInfoError.KINGS_ON_DIFFERENT_FILES;
			}
		}

		// check the castleString
		// no detailed error messages, because the castleString can be processed
		char wKSCSq = ' ';
		char wQSCSq = ' ';
		char bKSCSq = ' ';
		char bQSCSq = ' ';
		char wKSq = (char) ('A' + whitesKingSquare);
		char bKSq = (char) ('a' + blacksKingSquare - Chess.A8);
		for (int i = 0; i < castleString.length(); ++i) {
			char ch = castleString.charAt(i);
			if (ch >= 'A' && ch <= 'H') { // a castling option for White
				if (ch == wKSq) { // rook and king square are equal
					return CastlingInfoError.INVALID_CASTLING_INFORMATION;
				} else if (ch < wKSq) { // queenside
					if (wQSCSq != ' ') { // already set
						return CastlingInfoError.INVALID_CASTLING_INFORMATION;
					}
					wQSCSq = ch;
				} else if (ch > wKSq) { // kingside
					if (wKSCSq != ' ') { // already set
						return CastlingInfoError.INVALID_CASTLING_INFORMATION;
					}
					wKSCSq = ch;
				}
			} else if (ch >= 'a' && ch <= 'h') { // a castling option for Black
				if (ch == bKSq) { // rook and king square are equal
					return CastlingInfoError.INVALID_CASTLING_INFORMATION;
				} else if (ch < bKSq) { // queenside
					if (bQSCSq != ' ') { // already set
						return CastlingInfoError.INVALID_CASTLING_INFORMATION;
					}
					bQSCSq = ch;
				} else if (ch > bKSq) { // kingside
					if (bKSCSq != ' ') { // already set
						return CastlingInfoError.INVALID_CASTLING_INFORMATION;
					}
					bKSCSq = ch;
				}
			}
		}
		if (wKSCSq != ' ' && bKSCSq != ' ') { // kingside: both set
			if (Character.toLowerCase(wKSCSq) != bKSCSq) {
				return CastlingInfoError.INVALID_CASTLING_INFORMATION;
			}
		}
		if (wQSCSq != ' ' && bQSCSq != ' ') { // queenside: both set
			if (Character.toLowerCase(wQSCSq) != bQSCSq) {
				return CastlingInfoError.INVALID_CASTLING_INFORMATION;
			}
		}

		// next the rooks
		int whitesQueensideRookSquare = -1;
		int blacksQueensideRookSquare = -1;
		int whitesKingsideRookSquare = -1;
		int blacksKingsideRookSquare = -1;

		if (whiteCanCastle) { // find White's rooks
			for (int square = Chess.A1; square < whitesKingSquare; ++square) {
				if (pos.getStone(square) == Chess.WHITE_ROOK) {
					whitesQueensideRookSquare = square;
					break;
				}
			}
			for (int square = whitesKingSquare; square <= Chess.H1; ++square) {
				if (pos.getStone(square) == Chess.WHITE_ROOK) {
					whitesKingsideRookSquare = square;
					break;
				}
			}
		}
		if (blackCanCastle) { // find Black's rooks
			for (int square = Chess.A8; square < blacksKingSquare; ++square) {
				if (pos.getStone(square) == Chess.BLACK_ROOK) {
					blacksQueensideRookSquare = square;
					break;
				}
			}
			for (int square = blacksKingSquare; square <= Chess.H8; ++square) {
				if (pos.getStone(square) == Chess.BLACK_ROOK) {
					blacksKingsideRookSquare = square;
					break;
				}
			}
		}

		if (whiteCanCastle && blackCanCastle) {
			if (whitesQueensideRookSquare != blacksQueensideRookSquare % 8) {
				{
					String s = Character.toString(Chess.colToChar(whitesQueensideRookSquare));
					if (castleString.contains(s) && castleString.contains(s.toUpperCase())) {
						return CastlingInfoError.QUEENSIDE_ROOKS_ON_DIFFERENT_FILES;
					}
				}
				{
					String t = Character.toString(Chess.colToChar(blacksQueensideRookSquare % 8));
					if (castleString.contains(t) && castleString.contains(t.toUpperCase())) {
						return CastlingInfoError.QUEENSIDE_ROOKS_ON_DIFFERENT_FILES;
					}
				}
			}
			if (whitesKingsideRookSquare != blacksKingsideRookSquare % 8) {
				{
					String s = Character.toString(Chess.colToChar(whitesKingsideRookSquare));
					if (castleString.contains(s) && castleString.contains(s.toUpperCase())) {
						return CastlingInfoError.KINGSIDE_ROOKS_ON_DIFFERENT_FILES;
					}
				}
				{
					String t = Character.toString(Chess.colToChar(blacksKingsideRookSquare % 8));
					if (castleString.contains(t) && castleString.contains(t.toUpperCase())) {
						return CastlingInfoError.KINGSIDE_ROOKS_ON_DIFFERENT_FILES;
					}
				}
			}
		}

		// now the details
		int castles = ImmutablePosition.NO_CASTLES;

		int queensideRookSquare = -1;
		int kingsideRookSquare = -1;

		for (int i = 0; i < castleString.length(); ++i) {
			int ch = castleString.charAt(i);
			switch (ch) {
			case 'A':
				if (whitesQueensideRookSquare == Chess.A1) {
					queensideRookSquare = Chess.A1;
					castles |= ImmutablePosition.WHITE_LONG_CASTLE;
				} else {
					return CastlingInfoError.NO_WHITE_ROOK_ON_A1;
				}
				break;
			case 'B':
				if (whitesQueensideRookSquare == Chess.B1) {
					queensideRookSquare = Chess.B1;
					castles |= ImmutablePosition.WHITE_LONG_CASTLE;
				} else {
					return CastlingInfoError.NO_WHITE_ROOK_ON_B1;
				}
				break;
			case 'C':
				if (whitesKingSquare > Chess.C1) {
					if (whitesQueensideRookSquare == Chess.C1) {
						queensideRookSquare = Chess.C1;
						castles |= ImmutablePosition.WHITE_LONG_CASTLE;
					} else {
						return CastlingInfoError.NO_WHITE_ROOK_ON_C1;
					}
				} else if (whitesKingSquare < Chess.C1) {
					if (whitesKingsideRookSquare == Chess.C1) {
						kingsideRookSquare = Chess.C1;
						castles |= ImmutablePosition.WHITE_SHORT_CASTLE;
					} else {
						return CastlingInfoError.NO_WHITE_ROOK_ON_C1;
					}
				} else {
					return CastlingInfoError.NO_WHITE_ROOK_ON_C1;
				}
				break;
			case 'D':
				if (whitesKingSquare > Chess.D1) {
					if (whitesQueensideRookSquare == Chess.D1) {
						queensideRookSquare = Chess.D1;
						castles |= ImmutablePosition.WHITE_LONG_CASTLE;
					} else {
						return CastlingInfoError.NO_WHITE_ROOK_ON_D1;
					}
				} else if (whitesKingSquare < Chess.D1) {
					if (whitesKingsideRookSquare == Chess.D1) {
						kingsideRookSquare = Chess.D1;
						castles |= ImmutablePosition.WHITE_SHORT_CASTLE;
					} else {
						return CastlingInfoError.NO_WHITE_ROOK_ON_D1;
					}
				} else {
					return CastlingInfoError.NO_WHITE_ROOK_ON_D1;
				}
				break;
			case 'E':
				if (whitesKingSquare > Chess.E1) {
					if (whitesQueensideRookSquare == Chess.E1) {
						queensideRookSquare = Chess.E1;
						castles |= ImmutablePosition.WHITE_LONG_CASTLE;
					} else {
						return CastlingInfoError.NO_WHITE_ROOK_ON_E1;
					}
				} else if (whitesKingSquare < Chess.E1) {
					if (whitesKingsideRookSquare == Chess.E1) {
						kingsideRookSquare = Chess.E1;
						castles |= ImmutablePosition.WHITE_SHORT_CASTLE;
					} else {
						return CastlingInfoError.NO_WHITE_ROOK_ON_E1;
					}
				} else {
					return CastlingInfoError.NO_WHITE_ROOK_ON_E1;
				}
				break;
			case 'F':
				if (whitesKingSquare > Chess.F1) {
					if (whitesQueensideRookSquare == Chess.F1) {
						queensideRookSquare = Chess.F1;
						castles |= ImmutablePosition.WHITE_LONG_CASTLE;
					} else {
						return CastlingInfoError.NO_WHITE_ROOK_ON_F1;
					}
				} else if (whitesKingSquare < Chess.F1) {
					if (whitesKingsideRookSquare == Chess.F1) {
						kingsideRookSquare = Chess.F1;
						castles |= ImmutablePosition.WHITE_SHORT_CASTLE;
					} else {
						return CastlingInfoError.NO_WHITE_ROOK_ON_F1;
					}
				} else {
					return CastlingInfoError.NO_WHITE_ROOK_ON_F1;
				}
				break;
			case 'G':
				if (whitesKingsideRookSquare == Chess.G1) {
					kingsideRookSquare = Chess.G1;
					castles |= ImmutablePosition.WHITE_SHORT_CASTLE;
				} else {
					return CastlingInfoError.NO_WHITE_ROOK_ON_G1;
				}
				break;
			case 'H':
				if (whitesKingsideRookSquare == Chess.H1) {
					kingsideRookSquare = Chess.H1;
					castles |= ImmutablePosition.WHITE_SHORT_CASTLE;
				} else {
					return CastlingInfoError.NO_WHITE_ROOK_ON_H1;
				}
				break;
			case 'a':
				if (blacksQueensideRookSquare == Chess.A8) {
					queensideRookSquare = Chess.A1;
					castles |= ImmutablePosition.BLACK_LONG_CASTLE;
				} else {
					return CastlingInfoError.NO_BLACK_ROOK_ON_A8;
				}
				break;
			case 'b':
				if (blacksQueensideRookSquare == Chess.B8) {
					queensideRookSquare = Chess.B1;
					castles |= ImmutablePosition.BLACK_LONG_CASTLE;
				} else {
					return CastlingInfoError.NO_BLACK_ROOK_ON_B8;
				}
				break;
			case 'c':
				if (blacksKingSquare > Chess.C8) {
					if (blacksQueensideRookSquare == Chess.C8) {
						queensideRookSquare = Chess.C1;
						castles |= ImmutablePosition.BLACK_LONG_CASTLE;
					} else {
						return CastlingInfoError.NO_BLACK_ROOK_ON_C8;
					}
				} else if (blacksKingSquare < Chess.C8) {
					if (blacksKingsideRookSquare == Chess.C8) {
						kingsideRookSquare = Chess.C1;
						castles |= ImmutablePosition.BLACK_SHORT_CASTLE;
					} else {
						return CastlingInfoError.NO_BLACK_ROOK_ON_C8;
					}
				} else {
					return CastlingInfoError.NO_BLACK_ROOK_ON_C8;
				}
				break;
			case 'd':
				if (blacksKingSquare > Chess.D8) {
					if (blacksQueensideRookSquare == Chess.D8) {
						queensideRookSquare = Chess.D1;
						castles |= ImmutablePosition.BLACK_LONG_CASTLE;
					} else {
						return CastlingInfoError.NO_BLACK_ROOK_ON_D8;
					}
				} else if (blacksKingSquare < Chess.D8) {
					if (blacksKingsideRookSquare == Chess.D8) {
						kingsideRookSquare = Chess.D1;
						castles |= ImmutablePosition.BLACK_SHORT_CASTLE;
					} else {
						return CastlingInfoError.NO_BLACK_ROOK_ON_D8;
					}
				} else {
					return CastlingInfoError.NO_BLACK_ROOK_ON_D8;
				}
				break;
			case 'e':
				if (blacksKingSquare > Chess.E8) {
					if (blacksQueensideRookSquare == Chess.E8) {
						queensideRookSquare = Chess.E1;
						castles |= ImmutablePosition.BLACK_LONG_CASTLE;
					} else {
						return CastlingInfoError.NO_BLACK_ROOK_ON_E8;
					}
				} else if (blacksKingSquare < Chess.E8) {
					if (blacksKingsideRookSquare == Chess.E8) {
						kingsideRookSquare = Chess.E1;
						castles |= ImmutablePosition.BLACK_SHORT_CASTLE;
					} else {
						return CastlingInfoError.NO_BLACK_ROOK_ON_E8;
					}
				} else {
					return CastlingInfoError.NO_BLACK_ROOK_ON_E8;
				}
				break;
			case 'f':
				if (blacksKingSquare > Chess.F8) {
					if (blacksQueensideRookSquare == Chess.F8) {
						queensideRookSquare = Chess.F1;
						castles |= ImmutablePosition.BLACK_LONG_CASTLE;
					} else {
						return CastlingInfoError.NO_BLACK_ROOK_ON_F8;
					}
				} else if (blacksKingSquare < Chess.F8) {
					if (blacksKingsideRookSquare == Chess.F8) {
						kingsideRookSquare = Chess.F1;
						castles |= ImmutablePosition.BLACK_SHORT_CASTLE;
					} else {
						return CastlingInfoError.NO_BLACK_ROOK_ON_F8;
					}
				} else {
					return CastlingInfoError.NO_BLACK_ROOK_ON_F8;
				}
				break;
			case 'g':
				if (blacksKingsideRookSquare == Chess.G8) {
					kingsideRookSquare = Chess.G1;
					castles |= ImmutablePosition.BLACK_SHORT_CASTLE;
				} else {
					return CastlingInfoError.NO_BLACK_ROOK_ON_G8;
				}
				break;
			case 'h':
				if (blacksKingsideRookSquare == Chess.H8) {
					kingsideRookSquare = Chess.H1;
					castles |= ImmutablePosition.BLACK_SHORT_CASTLE;
				} else {
					return CastlingInfoError.NO_BLACK_ROOK_ON_H8;
				}
				break;
			default:
				break; // cannot be reached, since castleString matches [A-Ha-h]+.
			}
		}

		pos.setCastles(castles);
		pos.setChess960();
		pos.setChess960CastlingFiles(whitesKingSquare, queensideRookSquare, kingsideRookSquare);
		return CastlingInfoError.PROCESSED;
	}

	public static String getFEN(ImmutablePosition pos, int numberOfParts) {
		if (numberOfParts < 1) {
			return null;
		}
		StringBuilder sb = new StringBuilder();

		/* ========== 1st field : pieces ========== */
		int row = 7, col = 0;
		int blanks = 0;
		while (row >= 0) {
			int stone = pos.getStone(Chess.coorToSqiWithCheck(col, row));
			if (stone == Chess.NO_STONE) {
				blanks++;
			} else {
				if (blanks > 0) {
					sb.append(blanks);
					blanks = 0;
				}
				sb.append(stoneToFenChar(stone));
			}
			col++;
			if (col > 7) {
				if (blanks > 0) {
					sb.append(blanks);
					blanks = 0;
				}
				row--;
				col = 0;
				blanks = 0;
				if (row >= 0) {
					sb.append('/');
				}
			}
		}
		if (numberOfParts < 2) {
			return sb.toString();
		}

		/* ========== 2nd field : to play ========== */
		sb.append(' ').append(pos.getToPlay() == Chess.WHITE ? 'w' : 'b');
		if (numberOfParts < 3) {
			return sb.toString();
		}

		/* ========== 3rd field : castles ========== */
		sb.append(' ');
		int castles = pos.getCastles();
		if (castles != ImmutablePosition.NO_CASTLES) {
			if (pos.getVariant() == Variant.STANDARD) {
				if ((castles & ImmutablePosition.WHITE_SHORT_CASTLE) != 0) {
					sb.append('K');
				}
				if ((castles & ImmutablePosition.WHITE_LONG_CASTLE) != 0) {
					sb.append('Q');
				}
				if ((castles & ImmutablePosition.BLACK_SHORT_CASTLE) != 0) {
					sb.append('k');
				}
				if ((castles & ImmutablePosition.BLACK_LONG_CASTLE) != 0) {
					sb.append('q');
				}
			} else { // Chess960
				boolean stillEmpty = true;
				if ((castles & ImmutablePosition.WHITE_LONG_CASTLE) != 0 && pos.getChess960QueensideRookFile() != Chess.NO_FILE) {
					sb.append(Character.toUpperCase(Chess.colToChar(pos.getChess960QueensideRookFile())));
					stillEmpty = false;
				}
				if ((castles & ImmutablePosition.WHITE_SHORT_CASTLE) != 0 && pos.getChess960KingsideRookFile() != Chess.NO_FILE) {
					sb.append(Character.toUpperCase(Chess.colToChar(pos.getChess960KingsideRookFile())));
					stillEmpty = false;
				}
				if ((castles & ImmutablePosition.BLACK_LONG_CASTLE) != 0 && pos.getChess960QueensideRookFile() != Chess.NO_FILE) {
					sb.append(Chess.colToChar(pos.getChess960QueensideRookFile()));
					stillEmpty = false;
				}
				if ((castles & ImmutablePosition.BLACK_SHORT_CASTLE) != 0 && pos.getChess960KingsideRookFile() != Chess.NO_FILE) {
					sb.append(Chess.colToChar(pos.getChess960KingsideRookFile()));
					stillEmpty = false;
				}
				if (stillEmpty) {
					sb.append('-');
				}
			}
		} else {
			sb.append('-');
		}
		if (numberOfParts < 4) {
			return sb.toString();
		}

		/* ========== 4th field : ep square ========== */
		sb.append(' ');
		if (pos.getSqiEP() == Chess.NO_SQUARE) {
			sb.append('-');
		} else {
			sb.append(Chess.sqiToStr(pos.getSqiEP()));
		}
		if (numberOfParts < 5) {
			return sb.toString();
		}

		/* ========== 5th field : half move clock ========== */
		sb.append(' ').append(pos.getHalfMoveClock());
		if (numberOfParts < 6) {
			return sb.toString();
		}

		/* ========== 6th field : full move number ========== */
		sb.append(' ').append(pos.getPlyNumber() / 2 + 1);

		return sb.toString();
	}

	public static String getFEN(ImmutablePosition pos) {
		return getFEN(pos, 6);
	}

	public static boolean isShredderFEN(String fen) {
		String[] fenParts = fen.trim().split(" +");
		if (fenParts.length < 3) {
			return false;
		}
		return fenParts[2].matches("[A-Ha-h]+");
	}

	public static String switchColors(String fen) {
		String[] fenParts = fen.trim().split(" +");

		if (fenParts.length == 0) {
			throw new IllegalArgumentException("FEN::switchColors: Invalid FEN: empty string or only white spaces.");
		}

		StringBuilder newFen = new StringBuilder();

		/* ========== 1st field : pieces ========== */
		if (fenParts.length > 0) {
			String[] rows = fenParts[0].split("/");
			if (rows.length != 8) {
				throw new IllegalArgumentException(
						"Invalid FEN: invalid piece description, only " + rows.length + " rows found.");
			}

			for (int rowIndex = 7; rowIndex >= 0; --rowIndex) {
				if (rowIndex < 7) {
					newFen.append("/");
				}
				String row = rows[rowIndex];
				for (int charIndex = 0; charIndex < row.length(); ++charIndex) {
					char ch = row.charAt(charIndex);
					if (!Character.isDigit(ch)) {
						if (Character.isLowerCase(ch)) {
							newFen.append(Character.toUpperCase(ch));
						} else {
							newFen.append(Character.toLowerCase(ch));
						}
					} else {
						newFen.append(ch);
					}
				}
			}
			newFen.append(' ');
		}

		/* ========== 2nd field : to play ========== */

		if (fenParts.length > 1) {
			String toPlay = fenParts[1].toLowerCase();
			if (toPlay.equals("w")) {
				newFen.append("b");
			} else {
				newFen.append("w");
			}
			newFen.append(' ');
		}

		/* ========== 3rd field : castles ========== */

		if (fenParts.length > 2) {
			String castles = fenParts[2];
			if (castles.equals("-")) {
				newFen.append(castles);
			} else {
				for (int charIndex = 0; charIndex < castles.length(); ++charIndex) {
					char ch = castles.charAt(charIndex);
					if (Character.isLowerCase(ch)) {
						newFen.append(Character.toUpperCase(ch));
					} else {
						newFen.append(Character.toLowerCase(ch));
					}
				}
			}
			newFen.append(' ');
		}

		/* ========== 4th field : ep square ========== */

		if (fenParts.length > 3) {
			String epSquare = fenParts[3];
			if (epSquare.equals("-")) {
				newFen.append(epSquare);
			} else {
				for (int charIndex = 0; charIndex < epSquare.length(); ++charIndex) {
					char ch = epSquare.charAt(charIndex);
					if (ch == '3') {
						newFen.append('6');
					} else if (ch == '6') {
						newFen.append('3');
					} else {
						newFen.append(ch);
					}
				}
			}
			newFen.append(' ');
		}

		/* ========== 5th field : half-move clock ==== */

		if (fenParts.length > 4) {
			newFen.append(fenParts[4]).append(' ');
		}

		/* ========== 6th field : full move number ========== */

		if (fenParts.length > 5) {
			newFen.append(fenParts[5]);
		}

		return newFen.toString();
	}

	public static String switchLeftAndRight(String fen) {
		String[] fenParts = fen.trim().split(" +");

		if (fenParts.length == 0) {
			throw new IllegalArgumentException("FEN::switchLeftAndRight: Invalid FEN: empty string or only white spaces.");
		}

		StringBuilder newFen = new StringBuilder();

		/* ========== 1st field : pieces ========== */
		if (fenParts.length > 0) {
			String[] rows = fenParts[0].split("/");
			if (rows.length != 8) {
				throw new IllegalArgumentException(
						"Invalid FEN: invalid piece description, only " + rows.length + " rows found.");
			}

			for (int rowIndex = 0; rowIndex < 8; ++rowIndex) {
				if (rowIndex > 0) {
					newFen.append("/");
				}
				StringBuilder row = new StringBuilder(rows[rowIndex]);
				newFen.append(row.reverse());
			}
			newFen.append(' ');
		}

		/* ========== 2nd field : to play ========== */

		if (fenParts.length > 1) {
			newFen.append(fenParts[1]).append(' ');
		}

		/* ========== 3rd field : castles ========== */

		if (fenParts.length > 2) {
			String castles = fenParts[2];
			if (castles.equals("-")) {
				newFen.append(castles);
			} else {
				for (int charIndex = 0; charIndex < castles.length(); ++charIndex) {
					char ch = castles.charAt(charIndex);
					newFen.append(switchCharsLeftAndRight(ch));
				}
			}
			newFen.append(' ');
		}

		/* ========== 4th field : ep square ========== */

		if (fenParts.length > 3) {
			String epSquare = fenParts[3];
			if (epSquare.equals("-")) {
				newFen.append(epSquare);
			} else {
				for (int charIndex = 0; charIndex < epSquare.length(); ++charIndex) {
					char ch = epSquare.charAt(charIndex);
					newFen.append(switchCharsLeftAndRight(ch));
				}
			}
			newFen.append(' ');
		}

		/* ========== 5th field : half-move clock ==== */

		if (fenParts.length > 4) {
			newFen.append(fenParts[4]).append(' ');
		}

		/* ========== 6th field : full move number ========== */

		if (fenParts.length > 5) {
			newFen.append(fenParts[5]);
		}

		return newFen.toString();
	}

	private static char switchCharsLeftAndRight(char ch) {
		return switch (ch) {
		case 'a' -> 'h';
		case 'b' -> 'g';
		case 'c' -> 'f';
		case 'd' -> 'e';
		case 'e' -> 'd';
		case 'f' -> 'c';
		case 'g' -> 'b';
		case 'h' -> 'a';
		case 'A' -> 'H';
		case 'B' -> 'G';
		case 'C' -> 'F';
		case 'D' -> 'E';
		case 'E' -> 'D';
		case 'F' -> 'C';
		case 'G' -> 'B';
		case 'H' -> 'A';
		// The following four cases are for Chess960 castling options;
		// they make no sense for standard chess.
		case 'k' -> 'q';
		case 'q' -> 'k';
		case 'K' -> 'Q';
		case 'Q' -> 'K';
		default -> ch;
		};
	}

	// Switch the position along the horizontal line in the middle of the board.
	// Castling and the en passant square are always void in the result.
	public static String switchTopAndBottom(String fen) {
		String[] fenParts = fen.trim().split(" +");

		if (fenParts.length == 0) {
			throw new IllegalArgumentException("FEN::switchTopAndBottom: Invalid FEN: empty string or only white spaces.");
		}

		StringBuilder newFen = new StringBuilder();

		/* ========== 1st field : pieces ========== */
		if (fenParts.length > 0) {
			String[] rows = fenParts[0].split("/");
			if (rows.length != 8) {
				throw new IllegalArgumentException(
						"FEN::switchTopAndBottom: Invalid FEN: invalid piece description, only " + rows.length + " rows found.");
			}

			for (int rowIndex = 7; rowIndex >= 0; --rowIndex) {
				newFen.append(rows[rowIndex]);
				if (rowIndex > 0) {
					newFen.append("/");
				}
			}
			newFen.append(' ');
		}

		/* ========== 2nd field : to play ========== */
		if (fenParts.length > 1) {
			newFen.append(fenParts[1]).append(' ');
		}

		/* ========== 3rd field : castles ========== */
		if (fenParts.length > 2) {
			newFen.append("- ");
		}

		/* ========== 4th field : ep square ========== */
		if (fenParts.length > 3) {
			newFen.append("- ");
		}

		/* ========== 5th field : half-move clock ==== */
		if (fenParts.length > 4) {
			newFen.append(fenParts[4]).append(' ');
		}

		/* ========== 6th field : full move number ========== */
		if (fenParts.length > 5) {
			newFen.append(fenParts[5]);
		}

		return newFen.toString();
	}

	// Reflect the position along the a1-h8 diagonal.
	//  Castling and the en passant square are always void in the result.
	public static String reflectAlongDiagonal(String fen) {
		PositionImpl pos;
		try {
			pos = new PositionImpl(fen, false);
		} catch (InvalidFenException ignore) {
			return null;
		}
		PositionImpl reflectedPos = new PositionImpl(0);
		for (int sqi = Chess.A1; sqi <= Chess.H8; ++sqi) {
			int stone = pos.getStone(sqi);
			if (stone != Chess.NO_STONE) {
				reflectedPos.setStone(Chess.getDiagonallyReflectedSquare(sqi), stone);
			}
		}
		reflectedPos.setToPlay(pos.getToPlay());
		reflectedPos.setHalfMoveClock(pos.getHalfMoveClock());
		reflectedPos.setPlyOffset(pos.getPlyOffset());

		String[] fenParts = fen.trim().split(" +");
		return reflectedPos.getFEN(Math.min(fenParts.length, 6));
	}

	public static String removePieces(String fen, int piece) {
		String[] fenParts = fen.trim().split(" +");

		if (fenParts.length == 0) {
			throw new IllegalArgumentException("FEN::switchLeftAndRight: Invalid FEN: empty string or only white spaces.");
		}

		StringBuilder newFen = new StringBuilder();

		/* ========== 1st field : pieces ========== */
		if (fenParts.length > 0) {
			String rows = fenParts[0];
			switch (piece) {
			case Chess.QUEEN:
				rows = rows.replace('Q', '1');
				rows = rows.replace('q', '1');
				break;
			case Chess.ROOK:
				rows = rows.replace('R', '1');
				rows = rows.replace('r', '1');
				break;
			case Chess.BISHOP:
				rows = rows.replace('B', '1');
				rows = rows.replace('b', '1');
				break;
			case Chess.KNIGHT:
				rows = rows.replace('N', '1');
				rows = rows.replace('n', '1');
				break;
			case Chess.PAWN:
				rows = rows.replace('P', '1');
				rows = rows.replace('p', '1');
				break;
			default:
				break;
			}

			int sum = 0;
			for (int i = 0; i < rows.length(); ++i) {
				char ch = rows.charAt(i);
				if (Character.isDigit(ch)) {
					sum += ch - '0';
				} else {
					if (sum > 0) {
						newFen.append(sum);
						sum = 0;
					}
					newFen.append(ch);
				}
			}
			if (sum > 0) {
				newFen.append(sum);
			}
			newFen.append(' ');

			/* ========== other fields ============= */

			for (int i = 1; i < fenParts.length; ++i) {
				newFen.append(fenParts[i]).append(' ');
			}
		}

		return newFen.toString();
	}

	/**
	 * This method returns an array of two strings which describe White's and Black's pieces in 
	 * the following manner: 'Kh8 Pc6' and 'Ka6 Ph5' for Reti's famous study. The Piece strings
	 * are sorted primarily by the piece letters, and secondarily by the squares. 
	 * 
	 * @param fen
	 * @return two strings as described above
	 * @throws InvalidFenException 
	 */
	public static String[] getWhiteAndBlackPieces(String fen) throws InvalidFenException {
		String[] pieceStr = new String[] { "", "" };
		String[] parts = fen.split(" +");
		if (parts.length == 0) {
			return pieceStr;
		}
		List<String> whites = new ArrayList<>();
		List<String> blacks = new ArrayList<>();
		String[] rows = parts[0].split("/");
		for (int rowIndex = 0; rowIndex < rows.length; ++rowIndex) {
			String row = rows[rowIndex];
			int colIndex = 0;
			for (int index = 0; index < row.length(); ++index) {
				char ch = row.charAt(index);
				if (ch >= '1' && ch <= '8') {
					int num = ch - '0';
					if (index + num > 8) {
						throw new InvalidFenException("Invalid FEN: too many pieces in row " + (rowIndex + 1));
					}
					colIndex += num;
				} else {
					String str = Chess.sqiToStr(Chess.coorToSqi(colIndex, 7 - rowIndex));
					switch (ch) {
					case 'K':
						whites.add("K" + str);
						break;
					case 'Q':
						whites.add("Q" + str);
						break;
					case 'R':
						whites.add("R" + str);
						break;
					case 'B':
						whites.add("B" + str);
						break;
					case 'N':
						whites.add("N" + str);
						break;
					case 'P':
						whites.add("P" + str);
						break;
					case 'k':
						blacks.add("K" + str);
						break;
					case 'q':
						blacks.add("Q" + str);
						break;
					case 'r':
						blacks.add("R" + str);
						break;
					case 'b':
						blacks.add("B" + str);
						break;
					case 'n':
						blacks.add("N" + str);
						break;
					case 'p':
						blacks.add("P" + str);
						break;
					}
					++colIndex;
				}
			}
		}
		MyComparator myComparator = new MyComparator();
		whites.sort(myComparator);
		blacks.sort(myComparator);

		StringBuilder sb = new StringBuilder("");
		for (String w : whites) {
			sb.append(w).append(" ");
		}
		pieceStr[0] = sb.toString().trim();

		sb = new StringBuilder("");
		for (String b : blacks) {
			sb.append(b).append(" ");
		}
		pieceStr[1] = sb.toString().trim();

		return pieceStr;
	}

	/* This class does the sorting:
	 * 	- first by piece letter in the order K, Q, R, B, N, and P
	 * 	- second by the following square. */
	private static class MyComparator implements Comparator<String> {

		@Override
		public int compare(String o1, String o2) {
			char ch1 = o1.charAt(0);
			char ch2 = o2.charAt(0);
			if (ch1 != ch2) {
				if (ch1 == 'K') {
					return -1;
				} else if (ch2 == 'K') {
					return 1;
				}
				if (ch1 == 'Q') {
					return -1;
				} else if (ch2 == 'Q') {
					return 1;
				}
				if (ch1 == 'R') {
					return -1;
				} else if (ch2 == 'R') {
					return 1;
				}
				if (ch1 == 'B') {
					return -1;
				} else if (ch2 == 'B') {
					return 1;
				}
				if (ch1 == 'N') {
					return -1;
				} else if (ch2 == 'N') {
					return 1;
				}
				return 0; // 'P'
			}
			ch1 = o1.charAt(1);
			ch2 = o2.charAt(1);
			if (ch1 != ch2) {
				return ch1 - ch2;
			}
			ch1 = o1.charAt(2);
			ch2 = o2.charAt(2);
			return ch1 - ch2;
		}
	}

	/**
	 * This method returns an array of two strings which describe White's and Black's pieces in 
	 * the following manner: 'wKh8 wPc6' and 'bKa6 bPh5' for Reti's famous study. The Piece strings
	 * are sorted primarily by the piece letters, and secondarily by the squares. 
	 * 
	 * @param fen
	 * @return two strings as described above
	 * @throws InvalidFenException 
	 */
	public static String[] getWhiteAndBlackPiecesWithColor(String fen) throws InvalidFenException {
		String[] pieceStr = getWhiteAndBlackPieces(fen);
		pieceStr[0] = pieceStr[0].replace("K", "wK").replaceAll("Q", "wQ").replaceAll("R", "wR").replaceAll("B", "wB")
				.replaceAll("N", "wN").replaceAll("P", "wP");
		pieceStr[1] = pieceStr[1].replace("K", "bK").replaceAll("Q", "bQ").replaceAll("R", "bR").replaceAll("B", "bB")
				.replaceAll("N", "bN").replaceAll("P", "bP");
		return pieceStr;
	}
}

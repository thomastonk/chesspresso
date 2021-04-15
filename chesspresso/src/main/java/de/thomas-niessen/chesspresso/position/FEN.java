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

import java.util.regex.Pattern;

import chesspresso.Chess;
import chesspresso.Variant;
import chesspresso.position.ImmutablePosition.Validity;

public class FEN {

	private static final char[] fenChars = { 'K', 'P', 'Q', 'R', 'B', 'N', '-', 'n', 'b', 'r', 'q', 'p', 'k' };

	public static int fenCharToStone(char ch) {
		for (int stone = Chess.MIN_STONE; stone <= Chess.MAX_STONE; stone++) {
			if (fenChars[stone - Chess.MIN_STONE] == ch)
				return stone;
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

	// ======================================================================
	// TN: The new implementation accepts a position description, and adds default
	// values for all other information, if necessary.

	private static final Pattern SQUARE_PATTERN = Pattern.compile("[a-h][1-8]");

	static void initFromFEN(MutablePosition pos, String fen, boolean validate) throws InvalidFenException {
		pos.clear();

		String[] fenParts = fen.trim().split(" +");
		if (fenParts.length == 0) {
			throw new InvalidFenException("Invalid FEN: empty string or only white spaces.");
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
			int castles = ImmutablePosition.NO_CASTLES;
			if (!castleString.equals("-")) {
				if (castleString.length() > 4) {
					throw new InvalidFenException(
							"Invalid FEN: expected castling information of length at most 4, found " + castleString);
				}
				if (castleString.matches("[kqKQ]+")) { // standard FEN encoding
					if (pos.getVariant() == Variant.STANDARD) { // no-Chess960
						for (int i = 0; i < castleString.length(); ++i) {
							char ch = castleString.charAt(i);
							if (ch == 'K') {
								if (pos.getStone(Chess.E1) == Chess.WHITE_KING && pos.getStone(Chess.H1) == Chess.WHITE_ROOK)
									castles |= ImmutablePosition.WHITE_SHORT_CASTLE;
							} else if (ch == 'Q') {
								if (pos.getStone(Chess.E1) == Chess.WHITE_KING && pos.getStone(Chess.A1) == Chess.WHITE_ROOK)
									castles |= ImmutablePosition.WHITE_LONG_CASTLE;
							} else if (ch == 'k') {
								if (pos.getStone(Chess.E8) == Chess.BLACK_KING && pos.getStone(Chess.H8) == Chess.BLACK_ROOK)
									castles |= ImmutablePosition.BLACK_SHORT_CASTLE;
							} else if (ch == 'q') {
								if (pos.getStone(Chess.E8) == Chess.BLACK_KING && pos.getStone(Chess.A8) == Chess.BLACK_ROOK)
									castles |= ImmutablePosition.BLACK_LONG_CASTLE;
							}
						}
						pos.setCastles(castles);
					} else {
						setChess960Castling(pos, castleString);
					}
				} else if (castleString.matches("[a-hA-HkqKQ]+")) {
					pos.setVariant(Variant.CHESS960);
					setChess960Castling(pos, castleString);
				} else {
					throw new InvalidFenException("Invalid castling options in FEN: " + castleString);
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
			pos.setPlyNumber(ply);
			pos.setPlyOffset(ply);
		} else { // default value
			int ply = pos.getToPlay() == Chess.WHITE ? 0 : 1;
			pos.setPlyNumber(ply);
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

	private static void setChess960Castling(MutablePosition pos, String castleString) {
		if (!castleString.matches("[a-hA-HkqKQ]+")) {
			throw new IllegalArgumentException("Invalid castling options in FEN: " + castleString);
		}

		pos.setVariant(Variant.CHESS960);

		boolean whiteCanCastle = castleString.matches(".*[A-HKQ]+.*");
		boolean blackCanCastle = castleString.matches(".*[a-hkq]+.*");

		// start with the kings
		int whitesKingSquare = -1;
		int blacksKingSquare = -1;

		if (whiteCanCastle) { // White's king square is relevant
			for (int square = Chess.A1; square <= Chess.H1; ++square) {
				if (pos.getStone(square) == Chess.WHITE_KING) {
					whitesKingSquare = square;
					break;
				}
			}
			if (whitesKingSquare == -1) {
				throw new IllegalArgumentException(
						"Invalid castling options in FEN: " + castleString + ", White's king is not on the first rank.");
			}
			if (whitesKingSquare == Chess.A1) {
				throw new IllegalArgumentException(
						"Invalid castling options in FEN: " + castleString + ", but White's king is on A1.");
			}
			if (whitesKingSquare == Chess.H1) {
				throw new IllegalArgumentException(
						"Invalid castling options in FEN: " + castleString + ", but White's king is on H1.");
			}
		}

		if (blackCanCastle) { // Black's king square is relevant
			for (int square = Chess.A8; square <= Chess.H8; ++square) {
				if (pos.getStone(square) == Chess.BLACK_KING) {
					blacksKingSquare = square;
					break;
				}
			}
			if (blacksKingSquare == -1) {
				throw new IllegalArgumentException(
						"Invalid castling options in FEN: " + castleString + ", Black's king is not on the eighth rank.");
			}
			if (blacksKingSquare == Chess.A8) {
				throw new IllegalArgumentException(
						"Invalid castling options in FEN: " + castleString + ", but Black's king is on A8.");
			}
			if (blacksKingSquare == Chess.H8) {
				throw new IllegalArgumentException(
						"Invalid castling options in FEN: " + castleString + ", but Black's king is on H8.");
			}
		}

		if (whitesKingSquare != -1 && blacksKingSquare != -1) {
			if (whitesKingSquare % 8 != blacksKingSquare % 8) {
				throw new IllegalArgumentException(
						"Invalid castling options in FEN: " + castleString + ", while kings are on different files.");
			}
		}

		// next the rooks (only
		int whitesQueensideRookSquare = -1;
		int blacksQueensideRookSquare = -1;
		int whitesKingsideRookSquare = -1;
		int blacksKingsideRookSquare = -1;

		if (whiteCanCastle) { // White's rooks
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
		if (blackCanCastle) { // Black's rooks
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
					if ((castleString.contains(s) || castleString.contains("q"))
							&& (castleString.contains(s.toUpperCase()) || castleString.contains("Q"))) {
						throw new IllegalArgumentException(
								"Invalid castling options in FEN: " + castleString + ", but queenside rooks on different files.");
					}
				}
				{
					String t = Character.toString(Chess.colToChar(blacksQueensideRookSquare % 8));
					if ((castleString.contains(t) || castleString.contains("q"))
							&& (castleString.contains(t.toUpperCase()) || castleString.contains("Q"))) {
						throw new IllegalArgumentException(
								"Invalid castling options in FEN: " + castleString + ", but queenside rooks on different files.");
					}
				}
			}
			if (whitesKingsideRookSquare != blacksKingsideRookSquare % 8) {
				{
					String s = Character.toString(Chess.colToChar(whitesKingsideRookSquare));
					if ((castleString.contains(s) || castleString.contains("k"))
							&& (castleString.contains(s.toUpperCase()) || castleString.contains("K"))) {
						throw new IllegalArgumentException(
								"Invalid castling options in FEN: " + castleString + ", but kingside rooks on different files.");
					}
				}
				{
					String t = Character.toString(Chess.colToChar(blacksKingsideRookSquare % 8));
					if ((castleString.contains(t) || castleString.contains("k"))
							&& (castleString.contains(t.toUpperCase()) || castleString.contains("K"))) {
						throw new IllegalArgumentException(
								"Invalid castling options in FEN: " + castleString + ", but kingside rooks on different files.");
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
			boolean needsException = false;
			switch (ch) {
			case 'A':
				if (whitesQueensideRookSquare == Chess.A1) {
					queensideRookSquare = Chess.A1;
					castles |= ImmutablePosition.WHITE_LONG_CASTLE;
				} else {
					needsException = true;
				}
				if (needsException) {
					throw new IllegalArgumentException(
							"Invalid castling options in FEN: " + castleString + ", but 'A' does not match a rook position.");
				}
				break;
			case 'B':
				if (whitesQueensideRookSquare == Chess.B1) {
					queensideRookSquare = Chess.B1;
					castles |= ImmutablePosition.WHITE_LONG_CASTLE;
				} else {
					needsException = true;
				}
				if (needsException) {
					throw new IllegalArgumentException(
							"Invalid castling options in FEN: " + castleString + ", but 'B' does not match a rook position.");
				}
				break;
			case 'C':
				if (whitesKingSquare > Chess.C1) {
					if (whitesQueensideRookSquare == Chess.C1) {
						queensideRookSquare = Chess.C1;
						castles |= ImmutablePosition.WHITE_LONG_CASTLE;
					} else {
						needsException = true;
					}
				} else if (whitesKingSquare < Chess.C1) {
					if (whitesKingsideRookSquare == Chess.C1) {
						kingsideRookSquare = Chess.C1;
						castles |= ImmutablePosition.WHITE_SHORT_CASTLE;
					} else {
						needsException = true;
					}
				} else {
					needsException = true;
				}
				if (needsException) {
					throw new IllegalArgumentException(
							"Invalid castling options in FEN: " + castleString + ", but 'C' does not match a rook position.");
				}
				break;
			case 'D':
				if (whitesKingSquare > Chess.D1) {
					if (whitesQueensideRookSquare == Chess.D1) {
						queensideRookSquare = Chess.D1;
						castles |= ImmutablePosition.WHITE_LONG_CASTLE;
					} else {
						needsException = true;
					}
				} else if (whitesKingSquare < Chess.D1) {
					if (whitesKingsideRookSquare == Chess.D1) {
						kingsideRookSquare = Chess.D1;
						castles |= ImmutablePosition.WHITE_SHORT_CASTLE;
					} else {
						needsException = true;
					}
				} else {
					needsException = true;
				}
				if (needsException) {
					throw new IllegalArgumentException(
							"Invalid castling options in FEN: " + castleString + ", but 'D' does not match a rook position.");
				}
				break;
			case 'E':
				if (whitesKingSquare > Chess.E1) {
					if (whitesQueensideRookSquare == Chess.E1) {
						queensideRookSquare = Chess.E1;
						castles |= ImmutablePosition.WHITE_LONG_CASTLE;
					} else {
						needsException = true;
					}
				} else if (whitesKingSquare < Chess.E1) {
					if (whitesKingsideRookSquare == Chess.E1) {
						kingsideRookSquare = Chess.E1;
						castles |= ImmutablePosition.WHITE_SHORT_CASTLE;
					} else {
						needsException = true;
					}
				} else {
					needsException = true;
				}
				if (needsException) {
					throw new IllegalArgumentException(
							"Invalid castling options in FEN: " + castleString + ", but 'E' does not match a rook position.");
				}
				break;
			case 'F':
				if (whitesKingSquare > Chess.F1) {
					if (whitesQueensideRookSquare == Chess.F1) {
						queensideRookSquare = Chess.F1;
						castles |= ImmutablePosition.WHITE_LONG_CASTLE;
					} else {
						needsException = true;
					}
				} else if (whitesKingSquare < Chess.F1) {
					if (whitesKingsideRookSquare == Chess.F1) {
						kingsideRookSquare = Chess.F1;
						castles |= ImmutablePosition.WHITE_SHORT_CASTLE;
					} else {
						needsException = true;
					}
				} else {
					needsException = true;
				}
				if (needsException) {
					throw new IllegalArgumentException(
							"Invalid castling options in FEN: " + castleString + ", but 'F' does not match a rook position.");
				}
				break;
			case 'G':
				if (whitesKingsideRookSquare == Chess.G1) {
					kingsideRookSquare = Chess.G1;
					castles |= ImmutablePosition.WHITE_SHORT_CASTLE;
				} else {
					needsException = true;
				}
				if (needsException) {
					throw new IllegalArgumentException(
							"Invalid castling options in FEN: " + castleString + ", but 'G' does not match a rook position.");
				}
				break;
			case 'H':
				if (whitesKingsideRookSquare == Chess.H1) {
					kingsideRookSquare = Chess.H1;
					castles |= ImmutablePosition.WHITE_SHORT_CASTLE;
				} else {
					needsException = true;
				}
				if (needsException) {
					throw new IllegalArgumentException(
							"Invalid castling options in FEN: " + castleString + ", but 'H' does not match a rook position.");
				}
				break;
			case 'a':
				if (blacksQueensideRookSquare == Chess.A8) {
					queensideRookSquare = Chess.A1;
					castles |= ImmutablePosition.BLACK_LONG_CASTLE;
				} else {
					needsException = true;
				}
				if (needsException) {
					throw new IllegalArgumentException(
							"Invalid castling options in FEN: " + castleString + ", but 'a' does not match a rook position.");
				}
				break;
			case 'b':
				if (blacksQueensideRookSquare == Chess.B8) {
					queensideRookSquare = Chess.B1;
					castles |= ImmutablePosition.BLACK_LONG_CASTLE;
				} else {
					needsException = true;
				}
				if (needsException) {
					throw new IllegalArgumentException(
							"Invalid castling options in FEN: " + castleString + ", but 'b' does not match a rook position.");
				}
				break;
			case 'c':
				if (blacksKingSquare > Chess.C8) {
					if (blacksQueensideRookSquare == Chess.C8) {
						queensideRookSquare = Chess.C1;
						castles |= ImmutablePosition.BLACK_LONG_CASTLE;
					} else {
						needsException = true;
					}
				} else if (blacksKingSquare < Chess.C8) {
					if (blacksKingsideRookSquare == Chess.C8) {
						kingsideRookSquare = Chess.C1;
						castles |= ImmutablePosition.BLACK_SHORT_CASTLE;
					} else {
						needsException = true;
					}
				} else {
					needsException = true;
				}
				if (needsException) {
					throw new IllegalArgumentException(
							"Invalid castling options in FEN: " + castleString + ", but 'c' does not match a rook position.");
				}
				break;
			case 'd':
				if (blacksKingSquare > Chess.D8) {
					if (blacksQueensideRookSquare == Chess.D8) {
						queensideRookSquare = Chess.D1;
						castles |= ImmutablePosition.BLACK_LONG_CASTLE;
					} else {
						needsException = true;
					}
				} else if (blacksKingSquare < Chess.D8) {
					if (blacksKingsideRookSquare == Chess.D8) {
						kingsideRookSquare = Chess.D1;
						castles |= ImmutablePosition.BLACK_SHORT_CASTLE;
					} else {
						needsException = true;
					}
				} else {
					needsException = true;
				}
				if (needsException) {
					throw new IllegalArgumentException(
							"Invalid castling options in FEN: " + castleString + ", but 'd' does not match a rook position.");
				}
				break;
			case 'e':
				if (blacksKingSquare > Chess.E8) {
					if (blacksQueensideRookSquare == Chess.E8) {
						queensideRookSquare = Chess.E1;
						castles |= ImmutablePosition.BLACK_LONG_CASTLE;
					} else {
						needsException = true;
					}
				} else if (blacksKingSquare < Chess.E8) {
					if (blacksKingsideRookSquare == Chess.E8) {
						kingsideRookSquare = Chess.E1;
						castles |= ImmutablePosition.BLACK_SHORT_CASTLE;
					} else {
						needsException = true;
					}
				} else {
					needsException = true;
				}
				if (needsException) {
					throw new IllegalArgumentException(
							"Invalid castling options in FEN: " + castleString + ", but 'e' does not match a rook position.");
				}
				break;
			case 'f':
				if (blacksKingSquare > Chess.F8) {
					if (blacksQueensideRookSquare == Chess.F8) {
						queensideRookSquare = Chess.F1;
						castles |= ImmutablePosition.BLACK_LONG_CASTLE;
					} else {
						needsException = true;
					}
				} else if (blacksKingSquare < Chess.F8) {
					if (blacksKingsideRookSquare == Chess.F8) {
						kingsideRookSquare = Chess.F1;
						castles |= ImmutablePosition.BLACK_SHORT_CASTLE;
					} else {
						needsException = true;
					}
				} else {
					needsException = true;
				}
				if (needsException) {
					throw new IllegalArgumentException(
							"Invalid castling options in FEN: " + castleString + ", but 'f' does not match a rook position.");
				}
				break;
			case 'g':
				if (blacksKingsideRookSquare == Chess.G8) {
					kingsideRookSquare = Chess.G1;
					castles |= ImmutablePosition.BLACK_SHORT_CASTLE;
				} else {
					needsException = true;
				}
				if (needsException) {
					throw new IllegalArgumentException(
							"Invalid castling options in FEN: " + castleString + ", but 'g' does not match a rook position.");
				}
				break;
			case 'h':
				if (blacksKingsideRookSquare == Chess.H8) {
					kingsideRookSquare = Chess.H1;
					castles |= ImmutablePosition.BLACK_SHORT_CASTLE;
				} else {
					needsException = true;
				}
				if (needsException) {
					throw new IllegalArgumentException(
							"Invalid castling options in FEN: " + castleString + ", but 'h' does not match a rook position.");
				}
				break;
			case 'K':
				kingsideRookSquare = whitesKingsideRookSquare;
				castles |= ImmutablePosition.WHITE_SHORT_CASTLE;
				break;
			case 'Q':
				queensideRookSquare = whitesQueensideRookSquare;
				castles |= ImmutablePosition.WHITE_LONG_CASTLE;
				break;
			case 'k':
				kingsideRookSquare = blacksKingsideRookSquare % 8;
				castles |= ImmutablePosition.BLACK_SHORT_CASTLE;
				break;
			case 'q':
				queensideRookSquare = blacksQueensideRookSquare % 8;
				castles |= ImmutablePosition.BLACK_LONG_CASTLE;
				break;
			default:
				break;
			}
		}

		pos.setCastles(castles);
		if (pos.getVariant() == Variant.CHESS960) {
			pos.setChess960CastlingFiles(whitesKingSquare, queensideRookSquare, kingsideRookSquare);
		}
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
				if (row >= 0)
					sb.append('/');
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
				if ((castles & ImmutablePosition.WHITE_SHORT_CASTLE) != 0)
					sb.append('K');
				if ((castles & ImmutablePosition.WHITE_LONG_CASTLE) != 0)
					sb.append('Q');
				if ((castles & ImmutablePosition.BLACK_SHORT_CASTLE) != 0)
					sb.append('k');
				if ((castles & ImmutablePosition.BLACK_LONG_CASTLE) != 0)
					sb.append('q');
			} else { // Chess960
				if ((castles & ImmutablePosition.WHITE_LONG_CASTLE) != 0)
					sb.append(Character.toUpperCase(Chess.colToChar(pos.getChess960QueensideRookFile())));
				if ((castles & ImmutablePosition.WHITE_SHORT_CASTLE) != 0)
					sb.append(Character.toUpperCase(Chess.colToChar(pos.getChess960KingsideRookFile())));
				if ((castles & ImmutablePosition.BLACK_LONG_CASTLE) != 0)
					sb.append(Chess.colToChar(pos.getChess960QueensideRookFile()));
				if ((castles & ImmutablePosition.BLACK_SHORT_CASTLE) != 0)
					sb.append(Chess.colToChar(pos.getChess960KingsideRookFile()));
			}
		} else {
			sb.append('-');
		}
		if (numberOfParts < 4) {
			return sb.toString();
		}

		/* ========== 4th field : ep square ========== */
		sb.append(' ');
		if (pos.getSqiEP() == Chess.NO_SQUARE)
			sb.append('-');
		else
			sb.append(Chess.sqiToStr(pos.getSqiEP()));
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
}

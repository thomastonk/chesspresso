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
package chesspresso;

/**
 * General chess-specific definition.
 *
 * <p>
 * The following methods are used often throughout the higher-level classes and
 * are therefore implemented as simple as possible. Sometimes, checking for
 * illegal arguments is left to the caller for optimized performance.
 *
 * <p>
 * To deal with squares, the concepts of square, column, and row index are
 * introduced. A square index is a number our of [0..63], a column (file) and
 * row (rank) are out of [0..7].
 *
 * <p>
 * A piece is an uncolored type of chessman, a stone is a piece plus a colored.
 * For instance, possible pieces would be: a knight, a pawn, a king. Possible
 * stones are: a white queen, a black pawn. Possible colors (or players) are:
 * white, black and nobody/anybody.
 *
 * @author Bernhard Seybold
 * 
 */
public class Chess {

	private Chess() {
	}

	/* ========== squares, coordinates ========== */

	public static final int NUM_OF_COLS = 8, NUM_OF_ROWS = 8, NUM_OF_SQUARES = NUM_OF_COLS * NUM_OF_ROWS;

	public static final int RES_WHITE_WINS = 0, RES_DRAW = 1, RES_BLACK_WINS = 2, RES_NOT_FINISHED = 3, NO_RES = -1;

	public static final int A8 = 56, B8 = 57, C8 = 58, D8 = 59, E8 = 60, F8 = 61, G8 = 62, H8 = 63, A7 = 48, B7 = 49, C7 = 50,
			D7 = 51, E7 = 52, F7 = 53, G7 = 54, H7 = 55, A6 = 40, B6 = 41, C6 = 42, D6 = 43, E6 = 44, F6 = 45, G6 = 46, H6 = 47,
			A5 = 32, B5 = 33, C5 = 34, D5 = 35, E5 = 36, F5 = 37, G5 = 38, H5 = 39, A4 = 24, B4 = 25, C4 = 26, D4 = 27, E4 = 28,
			F4 = 29, G4 = 30, H4 = 31, A3 = 16, B3 = 17, C3 = 18, D3 = 19, E3 = 20, F3 = 21, G3 = 22, H3 = 23, A2 = 8, B2 = 9,
			C2 = 10, D2 = 11, E2 = 12, F2 = 13, G2 = 14, H2 = 15, A1 = 0, B1 = 1, C1 = 2, D1 = 3, E1 = 4, F1 = 5, G1 = 6, H1 = 7;

	public static final int A_FILE = 0, B_FILE = 1, C_FILE = 2, D_FILE = 3, E_FILE = 4, F_FILE = 5, G_FILE = 6, H_FILE = 7;
	// These values are equal to the values of the squares in the first row.

	public static final int NO_COL = -1, NO_ROW = -1, NO_SQUARE = -1, NO_FILE = -1;

	/**
	 * Converts coordinates to square index.
	 *
	 * @param col the column (file)
	 * @param row the row (rank)
	 * @return the square index
	 */
	public static int coorToSqi(int col, int row) {
		return row * NUM_OF_COLS + col;
	}

	/**
	 * Converts coordinates to square index. Check column and row values.
	 *
	 * @param col the column (file)
	 * @param row the row (rank)
	 * @return the square index
	 */
	public static int coorToSqiWithCheck(int col, int row) {
		if (0 <= col && col < NUM_OF_COLS) {
			if (0 <= row && row < NUM_OF_ROWS) {
				return row * NUM_OF_COLS + col;
			}
		}
		return NO_SQUARE;
	}

	/**
	 * Extract the row of a square index.
	 *
	 * @param sqi the square index
	 * @return the row
	 */
	public static int sqiToRow(int sqi) {
		return sqi / NUM_OF_COLS;
	}

	/**
	 * Extract the row of a square index. Check the square index.
	 *
	 * @param sqi the square index
	 * @return the row
	 */
	public static int sqiToRowWithCheck(int sqi) {
		if (A1 <= sqi && sqi <= H8) {
			return sqi / NUM_OF_COLS;
		}
		return NO_ROW;
	}

	/**
	 * Extract the column of a square index.
	 *
	 * @param sqi the square index
	 * @return the column
	 */
	public static int sqiToCol(int sqi) {
		return sqi % NUM_OF_COLS;
	}

	/**
	 * Extract the column of a square index. Check the square index.
	 *
	 * @param sqi the square index
	 * @return the column
	 */
	public static int sqiToColWithCheck(int sqi) {
		if (A1 <= sqi && sqi <= H8) {
			return sqi % NUM_OF_COLS;
		}
		return NO_COL;
	}

	/**
	 * Returns the row difference from one square index to the other.
	 *
	 * @param sqi1 the one square index
	 * @param sqi2 the other square index
	 * @return the row difference from sqi1 to sqi2
	 */
	public static int deltaRow(int sqi1, int sqi2) {
		return (sqi2 / NUM_OF_COLS) - (sqi1 / NUM_OF_COLS);
	}

	/**
	 * Returns the column difference from one square index to the other.
	 *
	 * @param sqi1 the one square index
	 * @param sqi2 the other square index
	 * @return the column difference from sqi1 to sqi2
	 */
	public static int deltaCol(int sqi1, int sqi2) {
		return (sqi2 % NUM_OF_COLS) - (sqi1 % NUM_OF_COLS);
	}

	/**
	 * Returns the character of a column (file): 'a'..'h'.
	 *
	 * @param col the column
	 * @return the character representing the column
	 */
	public static char colToChar(int col) {
		final char[] c = { '-', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h' };
		return c[col + 1];
	}

	/**
	 * Returns the character of a row (rank): '1'..'8'.
	 *
	 * @param row the row
	 * @return the character representing the column
	 */
	public static char rowToChar(int row) {
		final char[] r = { '-', '1', '2', '3', '4', '5', '6', '7', '8' };
		return r[row + 1];
		// return String.valueOf('1' + (char)row);
	}

	/**
	 * Returns the algebraic representation of a square "a1".."h8".
	 *
	 * @param sqi the square
	 * @return the algebraic representation
	 */
	public static String sqiToStr(int sqi) {
		return String.valueOf(colToChar(sqiToCol(sqi))) + rowToChar(sqiToRow(sqi));
	}

	/**
	 * Returns the algebraic representation of a square "a1".."h8".
	 *
	 * @param col the column
	 * @param row the row
	 * @return the algebraic representation
	 */
	public static String sqiToStr(int col, int row) {
		return String.valueOf(colToChar(col)) + rowToChar(row);
	}

	/**
	 * Returns whether the square is white.
	 *
	 * @param sqi the square
	 * @return whether sqi is a white square
	 */
	public static boolean isWhiteSquare(int sqi) {
		return ((sqiToCol(sqi) + sqiToRow(sqi)) % 2) != 0;
	}

	/**
	 * Returns whether the two square are adjacent.
	 *
	 * @param sqi1 the first square
	 * @param sqi2 the second square
	 * @return whether sqi1 and sqi2 are adjacent
	 */
	public static boolean areSquaresAdjacent(int sqi1, int sqi2) {
		if (Chess.A1 <= sqi1 && sqi1 <= Chess.H8 && Chess.A1 <= sqi2 && sqi2 <= Chess.H8) {
			return Math.abs((sqi1 % 8) - (sqi2 % 8)) <= 1 && Math.abs((sqi1 / 8) - (sqi2 / 8)) <= 1;
		} else {
			return false;
		}
	}

	/**
	 * Returns the column represented by the character.
	 *
	 * @param ch the column character ('a'..'h')
	 * @return the column, or <code>NO_COL</code> if an illegal character is passed
	 */
	public static int charToCol(char ch) {
		if ((ch >= 'a') && (ch <= 'h')) {
			return ch - 'a';
		} else {
			return NO_COL;
		}
	}

	/**
	 * Returns the row represented by the character.
	 *
	 * @param ch the row character ('1'..'8')
	 * @return the column, or <code>NO_ROW</code> if an illegal character is passed
	 */
	public static int charToRow(char ch) {
		if ((ch >= '1') && (ch <= '8')) {
			return ch - '1';
		} else {
			return NO_ROW;
		}
	}

	/**
	 * Converts a square representation to a square index.
	 *
	 * @param s the algebraic square representation
	 * @return the square index, or <code>NO_SQUARE</code> if an illegal string is
	 *         passed
	 */
	public static int strToSqi(String s) {
		if (s == null || s.length() != 2) {
			return NO_SQUARE;
		}
		int col = charToCol(s.charAt(0));
		if (col == NO_COL) {
			return NO_SQUARE;
		}
		int row = charToRow(s.charAt(1));
		if (row == NO_ROW) {
			return NO_SQUARE;
		}
		return coorToSqi(col, row);
	}

	/**
	 * Converts a col and row character pair to a square index.
	 *
	 * @param colCh the row character
	 * @param rowCh the column character
	 * @return the square index, or <code>NO_SQUARE</code> if an illegal character
	 *         is passed
	 */
	public static int strToSqi(char colCh, char rowCh) {
		int col = charToCol(colCh);
		if (col == NO_COL) {
			return NO_SQUARE;
		}
		int row = charToRow(rowCh);
		if (row == NO_ROW) {
			return NO_SQUARE;
		}
		return coorToSqi(col, row);
	}

	/* ========== pieces and stones ========== */

	public static final short MIN_PIECE = 0, MAX_PIECE = 6,
			// promotion pieces are from 0 to 4 to allow compact coding of moves
			KING = 6, PAWN = 5, QUEEN = 4, ROOK = 3, BISHOP = 2, KNIGHT = 1, NO_PIECE = 0;

	public static final short MIN_STONE = -6, MAX_STONE = 6, WHITE_KING = -6, WHITE_PAWN = -5, WHITE_QUEEN = -4, WHITE_ROOK = -3,
			WHITE_BISHOP = -2, WHITE_KNIGHT = -1, BLACK_KING = 6, BLACK_PAWN = 5, BLACK_QUEEN = 4, BLACK_ROOK = 3,
			BLACK_BISHOP = 2, BLACK_KNIGHT = 1, NO_STONE = NO_PIECE;

	public static final char[] PIECE_CHARS = { ' ', 'N', 'B', 'R', 'Q', 'P', 'K' };

	/**
	 * Extracts the color of a stone.
	 *
	 * @param stone the colored piece
	 * @return the color of the stone
	 */
	public static int stoneToColor(int stone) {
		if (stone < 0) {
			return WHITE;
		} else if (stone > 0) {
			return BLACK;
		} else {
			return NOBODY;
		}
	}

	/**
	 * Check whether the stone is of a certain color.
	 *
	 * @param stone the colored piece
	 * @param color the color to test for
	 * @return the true iff the stone is of the given color
	 */
	public static boolean stoneHasColor(int stone, int color) {
		return (color == WHITE && stone < 0) || (color == BLACK && stone > 0);
	}

	/**
	 * Converts a stone to a piece (remove color info).
	 *
	 * @param stone the colored piece
	 * @return the piece
	 */
	public static int stoneToPiece(int stone) {
		if (stone < 0) {
			return -stone;
		} else {
			return stone;
		}
	}

	/**
	 * Converts a stone to a piece (remove color info).
	 *
	 * @param stone the colored piece
	 * @return the piece
	 */
	public static short stoneToPiece(short stone) {
		if (stone < 0) {
			return (short) -stone;
		} else {
			return stone;
		}
	}

	/**
	 * Change the color of the stone.
	 *
	 * @param stone the colored piece
	 * @return the stone with inverse color
	 */
	public static int getOpponentStone(int stone) {
		return -stone;
	}

	/**
	 * Converts a character to a piece.
	 *
	 * @param ch a piece character
	 * @return the piece represented by the character, or <code>NO_PIECE</code> if
	 *         illegal
	 */
	public static int charToPiece(char ch) {
		for (int piece = 0; piece < PIECE_CHARS.length; piece++) {
			if (PIECE_CHARS[piece] == ch) {
				return piece;
			}
		}
		return NO_PIECE;
	}

	/**
	 * Returns a character representing the piece.
	 *
	 * @param piece the piece
	 * @return the character representing the piece, or '?' if an illegal piece is
	 *         passed
	 */
	public static char pieceToChar(int piece) {
		if (piece < 0 || piece > MAX_PIECE) {
			return '?';
		}
		return PIECE_CHARS[piece];
	}

	/**
	 * Returns a character representing the stone.
	 *
	 * @param stone the stone
	 * @return the character representing the stone, or '?' if an illegal piece is
	 *         passed
	 */
	public static char stoneToChar(int stone) {
		if (stone < 0) {
			return PIECE_CHARS[-stone];
		} else {
			return PIECE_CHARS[stone];
		}
	}

	/**
	 * Converts a piece, color pair to a stone.
	 *
	 * @param piece the piece
	 * @param color the color
	 * @return the stone, or <code>NO_PIECE</code> if illegal
	 */
	public static int pieceToStone(int piece, int color) {
		if (color == WHITE) {
			return -piece;
		} else if (color == BLACK) {
			return piece;
		} else {
			return NO_PIECE;
		}
	}

	/* ========== players ========== */

	public static final int WHITE = 0, BLACK = 1, NOBODY = -1, ANYBODY = -2;

	/**
	 * Returns the opposite player.
	 *
	 * @param player the player (or color)
	 * @return the opposite player (color respectively)
	 */
	public static int otherPlayer(int player) {
		if (player >= 0) {
			return 1 - player;
		} else {
			return player;
		}
	}

	/* ========== plies, moves ========== */

	/**
	 * Converts a ply to a move number
	 *
	 * @param plyNumber the ply number, starting at 0
	 */
	public static int plyToMoveNumber(int plyNumber) {
		if (plyNumber < 0) { // -1 happens!
			return 0;
		} else {
			return plyNumber / 2 + 1;
		}
	}

	/**
	 * Returns the black/white-inverted piece.
	 */
	public static int getInvertedPiece(int piece) {
		return -piece;
	}

	/**
	 * Returns the inverted square in the following sense: A1 becomes A8, .., H1
	 * becomes H8, A2 becomes A7, .., .., A8 becomes A1, .., H8 becomes H1.
	 */
	public static int getInvertedSquare(int square) {
		return (7 - sqiToRow(square)) * 8 + sqiToCol(square);
	}

	/**
	 * Returns a string description of the file.
	 */
	public static String fileToString(int file) {
		return switch (file) {
		case A_FILE -> "a-file";
		case B_FILE -> "b-file";
		case C_FILE -> "c-file";
		case D_FILE -> "d-file";
		case E_FILE -> "e-file";
		case F_FILE -> "f-file";
		case G_FILE -> "g-file";
		case H_FILE -> "h-file";
		default -> "unknown file";
		};
	}

	public static boolean isSquareOnFile(int sqi, int file) {
		return (sqi - file) % 8 == 0;
	}
}

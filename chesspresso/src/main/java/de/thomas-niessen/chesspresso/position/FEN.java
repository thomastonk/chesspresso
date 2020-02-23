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

public class FEN {

    private static final char fenChars[] = { 'K', 'P', 'Q', 'R', 'B', 'N', '-', 'n', 'b', 'r', 'q', 'p', 'k' };

    public static final int fenCharToStone(char ch) {
	for (int stone = Chess.MIN_STONE; stone <= Chess.MAX_STONE; stone++) {
	    if (fenChars[stone - Chess.MIN_STONE] == ch)
		return stone;
	}
	return Chess.NO_STONE;
    }

    public static final char stoneToFenChar(int stone) {
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

    public static void initFromFEN(MutablePosition pos, String fen) throws IllegalArgumentException {
	initFromFEN(pos, fen, true);
    }

    // TN added 'boolean validate' and changed error texts:
    public static void initFromFEN(MutablePosition pos, String fen, boolean validate) throws IllegalArgumentException {
	pos.clear();

	String[] fenParts = fen.split(" +");
	if (fenParts.length == 0) {
	    throw new IllegalArgumentException("Malformed FEN: empty string or only white spaces.");
	}

	/* ========== 1st field : pieces ========== */
	String[] rows = fenParts[0].split("/");
	if (rows.length != 8) {
	    throw new IllegalArgumentException(
		    "Malformed FEN: invalid piece description, only " + rows.length + " rows found.");
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
			throw new IllegalArgumentException("Malformed FEN: too many pieces in row " + (rowIndex + 1));
		    }
		    for (int j = 0; j < num; ++j) {
			pos.setStone(Chess.coorToSqi(colIndex, 7 - rowIndex), Chess.NO_STONE);
			++colIndex;
		    }
		} else {
		    int stone = FEN.fenCharToStone(ch);
		    if (stone == Chess.NO_STONE) {
			throw new IllegalArgumentException("Malformed FEN: illegal piece char: " + ch);
		    }
		    pos.setStone(Chess.coorToSqi(colIndex, 7 - rowIndex), stone);
		    ++colIndex;
		}
	    }
	    if (colIndex != 8) {
		throw new IllegalArgumentException("Malformed FEN: missing pieces in row " + (rowIndex + 1));
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
		throw new IllegalArgumentException(
			"Malformed FEN: expected 'w' or 'b' as second field, but found " + fenParts[1]);
	    }
	} else { // default value
	    pos.setToPlay(Chess.WHITE);
	}

	/* ========== 3rd field : castles ========== */
	if (fenParts.length > 2) {
	    String castleString = fenParts[2];
	    int castles = ImmutablePosition.NO_CASTLES;
	    if (!castleString.equals("-")) {
		if (castleString.length() < 5) {
		    for (int i = 0; i < castleString.length(); ++i) {
			char ch = castleString.charAt(i);
			if (ch == 'K') {
			    if (pos.getStone(Chess.E1) == Chess.WHITE_KING
				    && pos.getStone(Chess.H1) == Chess.WHITE_ROOK)
				castles |= ImmutablePosition.WHITE_SHORT_CASTLE;
			} else if (ch == 'Q') {
			    if (pos.getStone(Chess.E1) == Chess.WHITE_KING
				    && pos.getStone(Chess.A1) == Chess.WHITE_ROOK)
				castles |= ImmutablePosition.WHITE_LONG_CASTLE;
			} else if (ch == 'k') {
			    if (pos.getStone(Chess.E8) == Chess.BLACK_KING
				    && pos.getStone(Chess.H8) == Chess.BLACK_ROOK)
				castles |= ImmutablePosition.BLACK_SHORT_CASTLE;
			} else if (ch == 'q') {
			    if (pos.getStone(Chess.E8) == Chess.BLACK_KING
				    && pos.getStone(Chess.A8) == Chess.BLACK_ROOK)
				castles |= ImmutablePosition.BLACK_LONG_CASTLE;
			} else
			    throw new IllegalArgumentException(
				    "Malformed FEN: illegal castling character " + ch + " in " + castleString);
		    }
		} else {
		    throw new IllegalArgumentException(
			    "Malformed FEN: expected castling information of length at most 4, found " + castleString);
		}
		pos.setCastles(castles);
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
		    throw new IllegalArgumentException("Malformed FEN: expected en passant square, found " + epSquare);
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
		throw new IllegalArgumentException(
			"Malformed FEN: tried to evaluate the half-move clock, found " + hmClock);
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
		throw new IllegalArgumentException(
			"Malformed FEN: tried to evaluate the move number, found " + fenParts[5]);
	    }
	    if (moveNumber < 0) {
		throw new IllegalArgumentException(
			"Malformed FEN: tried to evaluate the move number, found " + fenParts[5]);
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
	    pos.setFirstPlyNumber(ply);
	} else { // default value
	    int ply = pos.getToPlay() == Chess.WHITE ? 0 : 1;
	    pos.setPlyNumber(ply);
	    pos.setFirstPlyNumber(ply);
	}

	/* ============= check the resulting position ========== */
	if (validate) {
	    try {
		pos.internalValidate();
	    } catch (Exception e) {
		e.printStackTrace();
		throw new IllegalArgumentException("Malformed FEN: " + e.getMessage());
	    }
	}
    }

    public static String getFEN(ImmutablePosition pos, int numberOfParts) {
	if (numberOfParts < 1) {
	    return null;
	}
	StringBuffer sb = new StringBuffer();

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
	    if ((castles & ImmutablePosition.WHITE_SHORT_CASTLE) != 0)
		sb.append('K');
	    if ((castles & ImmutablePosition.WHITE_LONG_CASTLE) != 0)
		sb.append('Q');
	    if ((castles & ImmutablePosition.BLACK_SHORT_CASTLE) != 0)
		sb.append('k');
	    if ((castles & ImmutablePosition.BLACK_LONG_CASTLE) != 0)
		sb.append('q');
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
}

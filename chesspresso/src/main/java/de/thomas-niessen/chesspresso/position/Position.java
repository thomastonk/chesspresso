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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import chesspresso.Chess;
import chesspresso.Variant;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;

public final class Position extends AbstractMoveablePosition implements Serializable {

    private static final long serialVersionUID = 1L;
    private final static boolean DEBUG = false;
    private final static boolean PROFILE = false;

    private static long m_numIsAttacked = 0;
    private static long m_numDirectAttackers = 0;
    private static long m_numGetAllAttackers = 0;
    private static long m_numIsCheck = 0;
    private static long m_numIsMate = 0;
    private static long m_numIsStaleMate = 0;
    private static long m_numGetAllMoves = 0;
    private static long m_numPositions = 0;
    private static long m_numGetPinnedDirection = 0;
    private static long m_numDoMove = 0;
    private static long m_numLongsBackuped = 0;
    private static long m_numUndoMove = 0;
    private static long m_numSet = 0;
    private static long m_numGetSquare = 0;

    /*
     * =========================================================================
     */
    // Bit Board operations
    // put here for performance (inlining)
    // do before hashing!

    private final static long[] s_ofCol, s_ofRow, s_ofSquare;

    static {
	s_ofCol = new long[Chess.NUM_OF_COLS];
	for (int col = 0; col < Chess.NUM_OF_COLS; col++)
	    s_ofCol[col] = 0x0101010101010101L << col;

	s_ofRow = new long[Chess.NUM_OF_ROWS];
	for (int row = 0; row < Chess.NUM_OF_ROWS; row++)
	    s_ofRow[row] = 255L << (8 * row);

	s_ofSquare = new long[Chess.NUM_OF_SQUARES];
	for (int sqi = 0; sqi < Chess.NUM_OF_SQUARES; sqi++)
	    s_ofSquare[sqi] = 1L << sqi;
    }

    private static boolean isExactlyOneBitSet(long bb) {
	return bb != 0L && (bb & (bb - 1L)) == 0L;
    }

    private static int numOfBitsSet(long bb) {
	int num = 0;
	while (bb != 0L) {
	    bb &= bb - 1;
	    num++;
	}
	// System.out.println(bb + " " + num);
	return num;
    }

    public static long ofSquare(int sqi) {
	return s_ofSquare[sqi];
    }

    public static long ofCol(int col) {
	return s_ofCol[col];
    }

    public static long ofRow(int row) {
	return s_ofRow[row];
    }

    private static int getFirstSqi(long bb) {
	// inefficient for bb == 0L, test outside (in while loop condition)
	// if (bb == 0L) {
	// return Chess.NO_SQUARE;
	// } else {
	int sqi = 0;
	if ((bb & 0xFFFFFFFFL) == 0L) {
	    bb >>>= 32;
	    sqi += 32;
	} // BS: is this good for all VMs?
	if ((bb & 0xFFFFL) == 0L) {
	    bb >>>= 16;
	    sqi += 16;
	}
	if ((bb & 0xFFL) == 0L) {
	    bb >>>= 8;
	    sqi += 8;
	}
	if ((bb & 0xFL) == 0L) {
	    bb >>>= 4;
	    sqi += 4;
	}
	if ((bb & 0x3L) == 0L) {
	    bb >>>= 2;
	    sqi += 2;
	}
	if ((bb & 0x1L) == 0L) {
	    bb >>>= 1;
	    sqi += 1;
	}
	// while ((bb % 2L) == 0L) {bb >>>= 1; sqi++;} change from % to &
	return sqi;
	// }
    }

    public static long getFirstSqiBB(long bb) // returns 0 if no bit set,
					      // not -1!!!
    {
	return bb & -bb;
    }

    @SuppressWarnings("unused")
    private static String toString(long bb) {
	String ZEROS = "0000000000000000000000000000000000000000000000000000000000000000";
	String s = ZEROS + Long.toBinaryString(bb);
	return s.substring(s.length() - 64);
    }

    @SuppressWarnings("unused")
    private static void printBoard(long bb) {
	for (int row = Chess.NUM_OF_ROWS - 1; row >= 0; row--) {
	    for (int col = 0; col < Chess.NUM_OF_COLS; col++) {
		if ((bb & ofSquare(Chess.coorToSqi(col, row))) != 0L) {
		    System.out.print('x');
		} else {
		    System.out.print('.');
		}
	    }
	    System.out.println();
	}
    }

    /*
     * =========================================================================
     */
    // directions

    public static final int NO_DIR = -1, NUM_OF_DIRS = 8, SW = 0, S = 1, SE = 2, E = 3, NE = 4, N = 5, NW = 6, W = 7;
    // need to start there, to allow calculation between pawn move dir and pawn
    // capture dir

    private static final int[] DIR_SHIFT = { -9, -8, -7, 1, 9, 8, 7, -1 };
    private static final long[] RIM_BOARD;
    private static final int[][] DIR;
    private static final long[][] RAY;
    private static final long[][] SQUARES_BETWEEN;

    static {
	/*---------- RIM_BOARD ----------*/
	RIM_BOARD = new long[NUM_OF_DIRS];
	RIM_BOARD[S] = ofRow(0);
	RIM_BOARD[E] = ofCol(Chess.NUM_OF_COLS - 1);
	RIM_BOARD[N] = ofRow(Chess.NUM_OF_ROWS - 1);
	RIM_BOARD[W] = ofCol(0);
	RIM_BOARD[SW] = RIM_BOARD[S] | RIM_BOARD[W];
	RIM_BOARD[SE] = RIM_BOARD[S] | RIM_BOARD[E];
	RIM_BOARD[NE] = RIM_BOARD[N] | RIM_BOARD[E];
	RIM_BOARD[NW] = RIM_BOARD[N] | RIM_BOARD[W];

	/*---------- DIR, RAY, SQUARES_BETWEEN ----------*/
	DIR = new int[Chess.NUM_OF_SQUARES][];
	RAY = new long[Chess.NUM_OF_SQUARES][];
	SQUARES_BETWEEN = new long[Chess.NUM_OF_SQUARES][];
	for (int from = Chess.A1; from <= Chess.H8; from++) {
	    DIR[from] = new int[Chess.NUM_OF_SQUARES];
	    SQUARES_BETWEEN[from] = new long[Chess.NUM_OF_SQUARES];
	    for (int to = Chess.A1; to <= Chess.H8; to++) {
		DIR[from][to] = getDir(from, to);
		SQUARES_BETWEEN[from][to] = 0L;
		if (DIR[from][to] != NO_DIR) {
		    for (int sqi = from + DIR_SHIFT[DIR[from][to]]; sqi != to; sqi += DIR_SHIFT[DIR[from][to]]) {
			SQUARES_BETWEEN[from][to] |= ofSquare(sqi);
		    }
		    // System.out.println(Chess.sqiToStr(from) + " " +
		    // Chess.sqiToStr(to));
		    // ChBitBoard.printBoard(SQUARES_BETWEEN[from][to]);
		}
	    }
	    RAY[from] = new long[NUM_OF_DIRS];
	    for (int dir = 0; dir < NUM_OF_DIRS; dir++) {
		long bb = ofSquare(from);
		for (;;) {
		    RAY[from][dir] |= bb;
		    if ((bb & RIM_BOARD[dir]) != 0L)
			break;
		    if (DIR_SHIFT[dir] < 0)
			bb >>>= -DIR_SHIFT[dir];
		    else
			bb <<= DIR_SHIFT[dir];
		}
		RAY[from][dir] &= ~ofSquare(from);
	    }
	}
    }

    private static boolean isDiagonal(int dir) {
	return (dir & 1) == 0;
    }

    private static boolean areDirectionsParallel(int dir1, int dir2) {
	if (dir1 == NO_DIR || dir2 == NO_DIR) {
	    return false;
	}
	return (dir1 & 3) == (dir2 & 3);
    }

    private static int getDir(int from, int to) {
	// used to generate DIR[from][to]

	int dcol = Chess.deltaCol(from, to);
	int drow = Chess.deltaRow(from, to);

	if (Math.abs(dcol) != Math.abs(drow) && dcol != 0 && drow != 0)
	    return NO_DIR;

	dcol = sign(dcol);
	drow = sign(drow);
	if (dcol == -1 && drow == -1)
	    return SW;
	if (dcol == -1 && drow == 0)
	    return W;
	if (dcol == -1 && drow == 1)
	    return NW;
	if (dcol == 0 && drow == -1)
	    return S;
	if (dcol == 0 && drow == 1)
	    return N;
	if (dcol == 1 && drow == -1)
	    return SE;
	if (dcol == 1 && drow == 0)
	    return E;
	if (dcol == 1 && drow == 1)
	    return NE;
	return NO_DIR;
    }

    /*
     * =========================================================================
     */
    // pre-computed bit boards

    private static final long[] KNIGHT_ATTACKS = new long[Chess.NUM_OF_SQUARES];
    private static final long[] BISHOP_ATTACKS = new long[Chess.NUM_OF_SQUARES];
    private static final long[] ROOK_ATTACKS = new long[Chess.NUM_OF_SQUARES];
    private static final long[] QUEEN_ATTACKS = new long[Chess.NUM_OF_SQUARES];
    private static final long[] KING_ATTACKS = new long[Chess.NUM_OF_SQUARES];
    private static final long[] ALL_ATTACKS = new long[Chess.NUM_OF_SQUARES];
    private static final long[] WHITE_PAWN_MOVES = new long[Chess.NUM_OF_SQUARES];
    private static final long[] BLACK_PAWN_MOVES = new long[Chess.NUM_OF_SQUARES];
    private static final long[] WHITE_PAWN_ATTACKS = new long[Chess.NUM_OF_SQUARES];
    private static final long[] BLACK_PAWN_ATTACKS = new long[Chess.NUM_OF_SQUARES];

    private static final long WHITE_SHORT_CASTLE_EMPTY_MASK = ofSquare(Chess.F1) | ofSquare(Chess.G1);
    private static final long WHITE_LONG_CASTLE_EMPTY_MASK = ofSquare(Chess.D1) | ofSquare(Chess.C1)
	    | ofSquare(Chess.B1);
    private static final long BLACK_SHORT_CASTLE_EMPTY_MASK = ofSquare(Chess.F8) | ofSquare(Chess.G8);
    private static final long BLACK_LONG_CASTLE_EMPTY_MASK = ofSquare(Chess.D8) | ofSquare(Chess.C8)
	    | ofSquare(Chess.B8);

    private static final long WHITE_SHORT_CASTLE_KING_CHANGE_MASK = ofSquare(Chess.E1) | ofSquare(Chess.G1);
    private static final long WHITE_LONG_CASTLE_KING_CHANGE_MASK = ofSquare(Chess.E1) | ofSquare(Chess.C1);
    private static final long BLACK_SHORT_CASTLE_KING_CHANGE_MASK = ofSquare(Chess.E8) | ofSquare(Chess.G8);
    private static final long BLACK_LONG_CASTLE_KING_CHANGE_MASK = ofSquare(Chess.E8) | ofSquare(Chess.C8);

    private static final long WHITE_SHORT_CASTLE_ROOK_CHANGE_MASK = ofSquare(Chess.F1) | ofSquare(Chess.H1);
    private static final long WHITE_LONG_CASTLE_ROOK_CHANGE_MASK = ofSquare(Chess.D1) | ofSquare(Chess.A1);
    private static final long BLACK_SHORT_CASTLE_ROOK_CHANGE_MASK = ofSquare(Chess.F8) | ofSquare(Chess.H8);
    private static final long BLACK_LONG_CASTLE_ROOK_CHANGE_MASK = ofSquare(Chess.D8) | ofSquare(Chess.A8);

    static {
	for (int from = Chess.A1; from <= Chess.H8; from++) {
	    KNIGHT_ATTACKS[from] = 0L;
	    BISHOP_ATTACKS[from] = 0L;
	    ROOK_ATTACKS[from] = 0L;
	    KING_ATTACKS[from] = 0L;
	    WHITE_PAWN_MOVES[from] = 0L;
	    BLACK_PAWN_MOVES[from] = 0L;
	    WHITE_PAWN_ATTACKS[from] = 0L;
	    BLACK_PAWN_ATTACKS[from] = 0L;
	    for (int to = Chess.A1; to <= Chess.H8; to++) {
		if (to != from) {
		    long bbTo = ofSquare(to);
		    int dcol = Chess.deltaCol(from, to);
		    int drow = Chess.deltaRow(from, to);
		    if (Math.abs(dcol * drow) == 2) {
			KNIGHT_ATTACKS[from] |= bbTo;
		    } else if (dcol == drow || dcol == -drow) {
			BISHOP_ATTACKS[from] |= bbTo;
		    } else if (dcol * drow == 0) {
			ROOK_ATTACKS[from] |= bbTo;
		    }
		    if (Math.abs(dcol) <= 1 && Math.abs(drow) <= 1) {
			KING_ATTACKS[from] |= bbTo;
		    }
		    if (dcol == 0 && drow == 1)
			WHITE_PAWN_MOVES[from] |= bbTo;
		    if (dcol == 0 && drow == -1)
			BLACK_PAWN_MOVES[from] |= bbTo;
		    if (dcol == -1 && drow == 1)
			WHITE_PAWN_ATTACKS[from] |= bbTo;
		    if (dcol == 1 && drow == 1)
			WHITE_PAWN_ATTACKS[from] |= bbTo;
		    if (dcol == -1 && drow == -1)
			BLACK_PAWN_ATTACKS[from] |= bbTo;
		    if (dcol == 1 && drow == -1)
			BLACK_PAWN_ATTACKS[from] |= bbTo;
		}
	    }
	    QUEEN_ATTACKS[from] = BISHOP_ATTACKS[from] | ROOK_ATTACKS[from];
	    ALL_ATTACKS[from] = QUEEN_ATTACKS[from] | KNIGHT_ATTACKS[from];
	}
    }

    /*
     * =========================================================================
     */
    // settings for information flags in m_flags

    private final static int
    // FLAG_UNKNOWN = 0,
    FLAG_YES = 1, FLAG_NO = 2, FLAG_MASK = 0x3;
    private final static int TO_PLAY_SHIFT = 0, TO_PLAY_MASK = 0x01, CASTLES_SHIFT = 1, CASTLES_MASK = 0x0F,
	    SQI_EP_SHIFT = 5, SQI_EP_MASK = 0x7F, HASH_COL_EP_SHIFT = 12, HASH_COL_EP_MASK = 0x0F, CHECK_SHIFT = 16,
	    CHECK_MASK = FLAG_MASK, CAN_MOVE_SHIFT = 18, CAN_MOVE_MASK = FLAG_MASK, HALF_MOVE_CLOCK_SHIFT = 20,
	    HALF_MOVE_CLOCK_MASK = 0xFF, PLY_NUMBER_SHIFT = 28, PLY_NUMBER_MASK = 0x3FF;

    private final static int OTHER_CHANGE_MOVE = Move.OTHER_SPECIALS;

    // can use up to 47 bits (64 bits - 2 * 6 to store king squares - 5 for
    // change mask)

    /*
     * =========================================================================
     */

    private long m_bbWhites, m_bbBlacks, m_bbPawns, m_bbKnights, m_bbBishops, m_bbRooks;
    private int m_whiteKing = Chess.NO_SQUARE, m_blackKing = Chess.NO_SQUARE; // actually only a short (6 bit)
    private long m_flags;
    private long m_hashCode;

    private long[] m_bakStack;
    private int m_bakIndex;
    private short[] m_moveStack;
    private int m_moveStackIndex;

    private final short[] m_moves = new short[256]; // buffer for getAllMoves,
						    // allocated once for efficiency
    // TN: Is 256 large enough? It is said that
    // "R6R/3Q4/1Q4Q1/4Q3/2Q4Q/Q4Q2/pp1Q4/kBNN1KB1 w - - 0 1"
    // has the largest number of possible moves: 218! So, 256 should always work.

    private Variant m_variant = Variant.STANDARD;

    // only White's squares will be stored
    private int m_chess960KingFile = -1;
    private int m_chess960QueensideRookFile = -1;
    private int m_chess960KingsideRookFile = -1;

    /*
     * =========================================================================
     */

    public final int getWhitesKingSquare() {
	return m_whiteKing;
    }

    public final int getBlacksKingSquare() {
	return m_blackKing;
    }

    public long getAllPawnsBB() {
	return m_bbPawns;
    }

    public long getWhitePawnsBB() {
	return m_bbPawns & m_bbWhites;
    }

    /*
     * =========================================================================
     */

    public static Position createInitialPosition() {
	return new Position(FEN.START_POSITION, true);
    }

    public Position() {
	this(60); // make room for 120 plies
    }

    public Position(int bufferLength) {
	if (PROFILE)
	    m_numPositions++;

	m_bakStack = new long[4 * bufferLength]; // on average, we need about
						 // 3.75 longs to backup a
						 // position
	m_moveStack = new short[bufferLength];
	clear();
    }

    public Position(ImmutablePosition pos) {
	this();
	setPosition(pos);
    }

    public Position(String fen) throws IllegalArgumentException {
	this(fen, true);
    }

    public Position(String fen, boolean strict) throws IllegalArgumentException {
	this();
	FEN.initFromFEN(this, fen, strict);
    }

    /*
     * =========================================================================
     */

    @Override
    public void clear() {
	super.clear();
    }

    // TN: Introduced for special purposes; could it replace clear()?!
    public void clearAll() {
	super.clear();
	int index = 0;
	while (index < m_bakStack.length && m_bakStack[index] != 0L) {
	    m_bakStack[index] = 0L;
	    ++index;
	}
	m_bakIndex = 0;
	index = 0;
	while (index < m_moveStack.length && m_moveStack[index] != 0) {
	    m_moveStack[index] = 0;
	    ++index;
	}
	m_moveStackIndex = 0;
	index = 0;
	while (index < m_moves.length && m_moves[index] != 0) {
	    m_moves[index] = 0;
	    ++index;
	}
    }

    /*
     * =========================================================================
     */

    @Override
    public final int getToPlay() {
	return ((m_flags >> TO_PLAY_SHIFT) & TO_PLAY_MASK) == 0 ? Chess.WHITE : Chess.BLACK;
    }

    private int getNotToPlay() {
	return ((m_flags >> TO_PLAY_SHIFT) & TO_PLAY_MASK) != 0 ? Chess.WHITE : Chess.BLACK;
    }

    @Override
    public final boolean isSquareEmpty(int sqi) {
	return ((m_bbWhites | m_bbBlacks) & ofSquare(sqi)) == 0L;
    }

    @Override
    public final int getCastles() {
	return (int) (m_flags >> CASTLES_SHIFT) & CASTLES_MASK;
    }

    @Override
    public final int getSqiEP() {
	return (int) ((m_flags >> SQI_EP_SHIFT) & SQI_EP_MASK) + Chess.NO_SQUARE;
    }

    private int getHashColEP() {
	return (int) ((m_flags >> HASH_COL_EP_SHIFT) & HASH_COL_EP_MASK) + Chess.NO_SQUARE;
    }

    @Override
    public final int getHalfMoveClock() {
	return (int) (m_flags >> HALF_MOVE_CLOCK_SHIFT) & HALF_MOVE_CLOCK_MASK;
    }

    @Override
    public final int getPlyNumber() {
	return (int) (m_flags >> PLY_NUMBER_SHIFT) & PLY_NUMBER_MASK;
    }

    @Override
    public final long getHashCode() {
	return m_hashCode;
    }

    @Override
    public final int getStone(int sqi) {
	if (PROFILE)
	    m_numGetSquare++;

	long bbSqi = ofSquare(sqi);
	if ((m_bbWhites & bbSqi) != 0L) {
	    if ((m_bbPawns & bbSqi) != 0L)
		return Chess.WHITE_PAWN;
	    if ((m_bbBishops & bbSqi) != 0L)
		return ((m_bbRooks & bbSqi) != 0L ? Chess.WHITE_QUEEN : Chess.WHITE_BISHOP);
	    if ((m_bbKnights & bbSqi) != 0L)
		return Chess.WHITE_KNIGHT;
	    // TN: old implementation (failed, when the board had no kings during setup):
//	    if (sqi == m_whiteKing)
//		return Chess.WHITE_KING;
//	    return Chess.WHITE_ROOK;
	    if ((m_bbRooks & bbSqi) != 0L)
		return Chess.WHITE_ROOK;
	    return Chess.WHITE_KING;
	} else if ((m_bbBlacks & bbSqi) != 0L) {
	    if ((m_bbPawns & bbSqi) != 0L)
		return Chess.BLACK_PAWN;
	    if ((m_bbBishops & bbSqi) != 0L)
		return ((m_bbRooks & bbSqi) != 0L ? Chess.BLACK_QUEEN : Chess.BLACK_BISHOP);
	    if ((m_bbKnights & bbSqi) != 0L)
		return Chess.BLACK_KNIGHT;
	    // TN: old implementation (failed, when the board had no kings during setup):
//	    if (sqi == m_blackKing)
//		return Chess.BLACK_KING;
//	    return Chess.BLACK_ROOK;
	    if ((m_bbRooks & bbSqi) != 0L)
		return Chess.BLACK_ROOK;
	    return Chess.BLACK_KING;
	} else {
	    return Chess.NO_STONE;
	}
    }

    @Override
    public final int getPiece(int sqi) {
	if (PROFILE)
	    m_numGetSquare++;

	long bbSqi = ofSquare(sqi);
	if ((m_bbPawns & bbSqi) != 0L)
	    return Chess.PAWN;
	if ((m_bbKnights & bbSqi) != 0L)
	    return Chess.KNIGHT;
	if ((m_bbBishops & bbSqi) != 0L)
	    return ((m_bbRooks & bbSqi) != 0L ? Chess.QUEEN : Chess.BISHOP);
	if ((m_bbRooks & bbSqi) != 0L)
	    return Chess.ROOK;
	// TN: Once more the old implementation fails during setup:
//	if (sqi == m_whiteKing || sqi == m_blackKing)
//	    return Chess.KING;
	if (((m_bbWhites & bbSqi) != 0L && sqi == m_whiteKing) || ((m_bbBlacks & bbSqi) != 0L && sqi == m_blackKing))
	    return Chess.KING;
	return Chess.NO_PIECE;
    }

    @Override
    public final int getColor(int sqi) {
	if (PROFILE)
	    m_numGetSquare++;

	long bbSqi = ofSquare(sqi);
	if ((m_bbWhites & bbSqi) != 0L)
	    return Chess.WHITE;
	if ((m_bbBlacks & bbSqi) != 0L)
	    return Chess.BLACK;
	return Chess.NOBODY;
    }

    private long getBitBoard(int stone) {
	switch (stone) {
	case Chess.NO_STONE:
	    return 0L;
	case Chess.WHITE_KING:
	    return ofSquare(m_whiteKing);
	case Chess.WHITE_PAWN:
	    return m_bbPawns & m_bbWhites;
	case Chess.WHITE_KNIGHT:
	    return m_bbKnights & m_bbWhites;
	case Chess.WHITE_BISHOP:
	    return m_bbBishops & (~m_bbRooks) & m_bbWhites;
	case Chess.WHITE_ROOK:
	    return m_bbRooks & (~m_bbBishops) & m_bbWhites;
	case Chess.WHITE_QUEEN:
	    return m_bbBishops & m_bbRooks & m_bbWhites;
	case Chess.BLACK_KING:
	    return ofSquare(m_blackKing);
	case Chess.BLACK_PAWN:
	    return m_bbPawns & m_bbBlacks;
	case Chess.BLACK_KNIGHT:
	    return m_bbKnights & m_bbBlacks;
	case Chess.BLACK_BISHOP:
	    return m_bbBishops & (~m_bbRooks) & m_bbBlacks;
	case Chess.BLACK_ROOK:
	    return m_bbRooks & (~m_bbBishops) & m_bbBlacks;
	case Chess.BLACK_QUEEN:
	    return m_bbBishops & m_bbRooks & m_bbBlacks;
	default:
	    throw new RuntimeException("Unknown stone: " + stone);
	}
    }

    /*
     * =========================================================================
     */

    @Override
    public final void setStone(int sqi, int stone) {
	if (PROFILE)
	    m_numSet++;

	if (DEBUG)
	    System.out.println("Set " + Chess.stoneToChar(stone) + " to " + Chess.sqiToStr(sqi));

	/*---------- remove an old king on another square ----------*/
	if (stone == Chess.WHITE_KING && m_whiteKing != Chess.NO_SQUARE && m_whiteKing != sqi) {
	    setStone(m_whiteKing, Chess.NO_STONE);
	} else if (stone == Chess.BLACK_KING && m_blackKing != Chess.NO_SQUARE && m_blackKing != sqi) {
	    setStone(m_blackKing, Chess.NO_STONE);
	}

	int old = getStone(sqi);
	if (old != stone) {
	    long bbSqi = ofSquare(sqi);

	    /*---------- remove stone from sqi ----------*/
	    switch (old) {
	    case Chess.NO_STONE:
		break;
	    case Chess.WHITE_KING:
		m_bbWhites &= ~bbSqi;
		m_whiteKing = Chess.NO_SQUARE;
		break;
	    case Chess.WHITE_PAWN:
		m_bbWhites &= ~bbSqi;
		m_bbPawns &= ~bbSqi;
		break;
	    case Chess.WHITE_KNIGHT:
		m_bbWhites &= ~bbSqi;
		m_bbKnights &= ~bbSqi;
		break;
	    case Chess.WHITE_BISHOP:
		m_bbWhites &= ~bbSqi;
		m_bbBishops &= ~bbSqi;
		break;
	    case Chess.WHITE_ROOK:
		m_bbWhites &= ~bbSqi;
		m_bbRooks &= ~bbSqi;
		break;
	    case Chess.WHITE_QUEEN:
		m_bbWhites &= ~bbSqi;
		m_bbBishops &= ~bbSqi;
		m_bbRooks &= ~bbSqi;
		break;
	    case Chess.BLACK_KING:
		m_bbBlacks &= ~bbSqi;
		m_blackKing = Chess.NO_SQUARE;
		break;
	    case Chess.BLACK_PAWN:
		m_bbBlacks &= ~bbSqi;
		m_bbPawns &= ~bbSqi;
		break;
	    case Chess.BLACK_KNIGHT:
		m_bbBlacks &= ~bbSqi;
		m_bbKnights &= ~bbSqi;
		break;
	    case Chess.BLACK_BISHOP:
		m_bbBlacks &= ~bbSqi;
		m_bbBishops &= ~bbSqi;
		break;
	    case Chess.BLACK_ROOK:
		m_bbBlacks &= ~bbSqi;
		m_bbRooks &= ~bbSqi;
		break;
	    case Chess.BLACK_QUEEN:
		m_bbBlacks &= ~bbSqi;
		m_bbBishops &= ~bbSqi;
		m_bbRooks &= ~bbSqi;
		break;
	    }

	    /*---------- add new stone to sqi ----------*/
	    switch (stone) {
	    case Chess.NO_STONE:
		break;
	    case Chess.WHITE_KING:
		m_bbWhites |= bbSqi;
		m_whiteKing = sqi;
		break;
	    case Chess.WHITE_PAWN:
		m_bbWhites |= bbSqi;
		m_bbPawns |= bbSqi;
		break;
	    case Chess.WHITE_KNIGHT:
		m_bbWhites |= bbSqi;
		m_bbKnights |= bbSqi;
		break;
	    case Chess.WHITE_BISHOP:
		m_bbWhites |= bbSqi;
		m_bbBishops |= bbSqi;
		break;
	    case Chess.WHITE_ROOK:
		m_bbWhites |= bbSqi;
		m_bbRooks |= bbSqi;
		break;
	    case Chess.WHITE_QUEEN:
		m_bbWhites |= bbSqi;
		m_bbBishops |= bbSqi;
		m_bbRooks |= bbSqi;
		break;
	    case Chess.BLACK_KING:
		m_bbBlacks |= bbSqi;
		m_blackKing = sqi;
		break;
	    case Chess.BLACK_PAWN:
		m_bbBlacks |= bbSqi;
		m_bbPawns |= bbSqi;
		break;
	    case Chess.BLACK_KNIGHT:
		m_bbBlacks |= bbSqi;
		m_bbKnights |= bbSqi;
		break;
	    case Chess.BLACK_BISHOP:
		m_bbBlacks |= bbSqi;
		m_bbBishops |= bbSqi;
		break;
	    case Chess.BLACK_ROOK:
		m_bbBlacks |= bbSqi;
		m_bbRooks |= bbSqi;
		break;
	    case Chess.BLACK_QUEEN:
		m_bbBlacks |= bbSqi;
		m_bbBishops |= bbSqi;
		m_bbRooks |= bbSqi;
		break;
	    }

	    /*---------- hash value ----------*/
	    if (old != Chess.NO_STONE)
		m_hashCode ^= s_hashMod[sqi][old - Chess.MIN_STONE];
	    if (stone != Chess.NO_STONE)
		m_hashCode ^= s_hashMod[sqi][stone - Chess.MIN_STONE];
	    // System.out.println("hash code set: " + m_hashCode);

	    /*---------- listeners ----------*/
	    if (m_notifyListeners)
		fireSquareChanged(sqi);
	}

    }

    @Override
    public final void setPlyNumber(int plyNumber) {
	// By the bit operations a ply number will always be between 0 and 1023!
	if (plyNumber < 0 || plyNumber > 1023) {
	    System.err.println("Invalid ply number: " + plyNumber);
	}
	if (DEBUG)
	    System.out.println("setPlyNumber " + plyNumber);
	long flags = m_flags;
	m_flags &= ~((long) PLY_NUMBER_MASK << PLY_NUMBER_SHIFT);
	m_flags |= (long) plyNumber << PLY_NUMBER_SHIFT;
	if (m_flags != flags) {
	    if (m_notifyListeners)
		firePlyNumberChanged();
	}
    }

    private void incPlyNumber() {
	// By the bit operations a ply number will always be between 0 and 1023!
	if (DEBUG)
	    System.out.println("incPlyNumber");
	m_flags += 1L << PLY_NUMBER_SHIFT;
	if (m_notifyListeners)
	    firePlyNumberChanged();
    }

    @Override
    public void setHalfMoveClock(int halfMoveClock) {
	// By the bit operations a half move clock number will always be between 0 and
	// 255!
	if (DEBUG)
	    System.out.println("setHalfMoveClock " + halfMoveClock);
	long flags = m_flags;
	m_flags &= ~(HALF_MOVE_CLOCK_MASK << HALF_MOVE_CLOCK_SHIFT);
	m_flags |= (long) halfMoveClock << HALF_MOVE_CLOCK_SHIFT;
	if (m_flags != flags) {
	    if (m_notifyListeners)
		fireHalfMoveClockChanged();
	}
    }

    @Override
    public final void setCastles(int castles) {
	if (DEBUG)
	    System.out.println("setCastles " + castles);
	int oldCastles = getCastles();
	if (oldCastles != castles) {
	    m_flags &= ~(CASTLES_MASK << CASTLES_SHIFT);
	    m_flags |= castles << CASTLES_SHIFT;
	    /*---------- hash value ----------*/
	    m_hashCode ^= s_hashCastleMod[oldCastles];
	    m_hashCode ^= s_hashCastleMod[castles];
	    // System.out.println("hash code castles: " + m_hashCode);
	    /*---------- listeners ----------*/
	    if (m_notifyListeners)
		fireCastlesChanged();
	}
    }

    @Override
    public void setSqiEP(int sqiEP) {
	if (DEBUG)
	    System.out.println("setSqiEP " + sqiEP);
	if (getSqiEP() != sqiEP) {
	    m_flags &= ~(SQI_EP_MASK << SQI_EP_SHIFT);
	    m_flags |= (sqiEP - Chess.NO_SQUARE) << SQI_EP_SHIFT;

	    /*---------- hash value ----------*/
	    int hashColEP = getHashColEP();
	    if (hashColEP != Chess.NO_SQUARE)
		m_hashCode ^= s_hashEPMod[hashColEP];

	    hashColEP = (sqiEP == Chess.NO_COL ? Chess.NO_COL : Chess.sqiToCol(sqiEP));
	    // ignore ep square for hashing if there is no opponent pawn to
	    // actually capture the pawn ep
	    // only in this case is the position different

	    // if (sqiEP < 0 || sqiEP > 63) {
	    // System.out.println(sqiEP);
	    // }

	    if (sqiEP != Chess.NO_COL) {
		if (sqiEP < Chess.A4) { // test is independent of whether toplay
					// is set before or afterwards
		    if ((WHITE_PAWN_ATTACKS[sqiEP] & m_bbPawns & m_bbBlacks) == 0L) {
			hashColEP = Chess.NO_COL;
		    }
		} else {
		    if ((BLACK_PAWN_ATTACKS[sqiEP] & m_bbPawns & m_bbWhites) == 0L) {
			hashColEP = Chess.NO_COL;
		    }
		}
		if (hashColEP != Chess.NO_COL)
		    m_hashCode ^= s_hashEPMod[hashColEP];
	    }
	    m_flags &= ~(HASH_COL_EP_MASK << HASH_COL_EP_SHIFT);
	    // encode column of ep square in hash code (NO_SQUARE if no ep)
	    m_flags |= (hashColEP - Chess.NO_SQUARE) << HASH_COL_EP_SHIFT;
	    // System.out.println("hash code ep: " + m_hashCode);

	    /*---------- listeners ----------*/
	    if (m_notifyListeners)
		fireSqiEPChanged();
	}
    }

    @Override
    public final void setToPlay(int toPlay) {
	if (DEBUG)
	    System.out.println("setToPlay " + toPlay);
	if (toPlay != getToPlay()) {
	    toggleToPlay();
	}
    }

    @Override
    public final void toggleToPlay() {
	if (DEBUG)
	    System.out.println("toggleToPlay");
	m_flags ^= (TO_PLAY_MASK << TO_PLAY_SHIFT);
	/*---------- hash value ----------*/
	m_hashCode ^= HASH_TOPLAY_MULT;
	// System.out.println("hash code toPlay: " + m_hashCode);
	/*---------- listeners ----------*/
	if (m_notifyListeners)
	    fireToPlayChanged();
    }

    private void setMove(short move) throws IllegalMoveException {
	if (DEBUG) {
	    System.out.println(getPlyNumber() + ": " + Move.getString(move));
	}

	boolean increaseHalfMoveClock = true;
	int sqiEP = Chess.NO_SQUARE;
	long squaresChanged = 0L;

	if (!Move.isNullMove(move)) {
	    /*---------- moves the pieces ----------*/
	    if (Move.isCastle(move)) {
		if (getToPlay() == Chess.WHITE) {
		    if (Move.isShortCastle(move)) {
			squaresChanged = WHITE_SHORT_CASTLE_KING_CHANGE_MASK | WHITE_SHORT_CASTLE_ROOK_CHANGE_MASK;
			m_bbWhites ^= WHITE_SHORT_CASTLE_KING_CHANGE_MASK | WHITE_SHORT_CASTLE_ROOK_CHANGE_MASK;
			m_whiteKing = Chess.G1;
			m_bbRooks ^= WHITE_SHORT_CASTLE_ROOK_CHANGE_MASK;
			m_hashCode ^= s_hashMod[Chess.E1][Chess.WHITE_KING - Chess.MIN_STONE];
			m_hashCode ^= s_hashMod[Chess.F1][Chess.WHITE_ROOK - Chess.MIN_STONE];
			m_hashCode ^= s_hashMod[Chess.G1][Chess.WHITE_KING - Chess.MIN_STONE];
			m_hashCode ^= s_hashMod[Chess.H1][Chess.WHITE_ROOK - Chess.MIN_STONE];
		    } else {
			squaresChanged = WHITE_LONG_CASTLE_KING_CHANGE_MASK | WHITE_LONG_CASTLE_ROOK_CHANGE_MASK;
			m_bbWhites ^= WHITE_LONG_CASTLE_KING_CHANGE_MASK | WHITE_LONG_CASTLE_ROOK_CHANGE_MASK;
			m_whiteKing = Chess.C1;
			m_bbRooks ^= WHITE_LONG_CASTLE_ROOK_CHANGE_MASK;
			m_hashCode ^= s_hashMod[Chess.E1][Chess.WHITE_KING - Chess.MIN_STONE];
			m_hashCode ^= s_hashMod[Chess.D1][Chess.WHITE_ROOK - Chess.MIN_STONE];
			m_hashCode ^= s_hashMod[Chess.C1][Chess.WHITE_KING - Chess.MIN_STONE];
			m_hashCode ^= s_hashMod[Chess.A1][Chess.WHITE_ROOK - Chess.MIN_STONE];
		    }
		    excludeCastles(WHITE_CASTLE);
		} else {
		    if (Move.isShortCastle(move)) {
			squaresChanged = BLACK_SHORT_CASTLE_KING_CHANGE_MASK | BLACK_SHORT_CASTLE_ROOK_CHANGE_MASK;
			m_bbBlacks ^= BLACK_SHORT_CASTLE_KING_CHANGE_MASK | BLACK_SHORT_CASTLE_ROOK_CHANGE_MASK;
			m_blackKing = Chess.G8;
			m_bbRooks ^= BLACK_SHORT_CASTLE_ROOK_CHANGE_MASK;
			m_hashCode ^= s_hashMod[Chess.E8][Chess.BLACK_KING - Chess.MIN_STONE];
			m_hashCode ^= s_hashMod[Chess.F8][Chess.BLACK_ROOK - Chess.MIN_STONE];
			m_hashCode ^= s_hashMod[Chess.G8][Chess.BLACK_KING - Chess.MIN_STONE];
			m_hashCode ^= s_hashMod[Chess.H8][Chess.BLACK_ROOK - Chess.MIN_STONE];
		    } else {
			squaresChanged = BLACK_LONG_CASTLE_KING_CHANGE_MASK | BLACK_LONG_CASTLE_ROOK_CHANGE_MASK;
			m_bbBlacks ^= BLACK_LONG_CASTLE_KING_CHANGE_MASK | BLACK_LONG_CASTLE_ROOK_CHANGE_MASK;
			m_blackKing = Chess.C8;
			m_bbRooks ^= BLACK_LONG_CASTLE_ROOK_CHANGE_MASK;
			m_hashCode ^= s_hashMod[Chess.E8][Chess.BLACK_KING - Chess.MIN_STONE];
			m_hashCode ^= s_hashMod[Chess.D8][Chess.BLACK_ROOK - Chess.MIN_STONE];
			m_hashCode ^= s_hashMod[Chess.C8][Chess.BLACK_KING - Chess.MIN_STONE];
			m_hashCode ^= s_hashMod[Chess.A8][Chess.BLACK_ROOK - Chess.MIN_STONE];
		    }
		    excludeCastles(BLACK_CASTLE);
		}
	    } else if (Move.isCastleChess960(move)) {
		// the implementation with setStone is less efficient, but much easier
		int kingSquare = Move.getFromSqi(move);
		int rookSquare = Move.getToSqi(move);
		if (getToPlay() == Chess.WHITE) {
		    if (kingSquare < rookSquare) { // a white short castle
			setStone(kingSquare, Chess.NO_STONE);
			setStone(rookSquare, Chess.NO_STONE);
			setStone(Chess.G1, Chess.WHITE_KING);
			setStone(Chess.F1, Chess.WHITE_ROOK);
		    } else { // a white long castle
			setStone(kingSquare, Chess.NO_STONE);
			setStone(rookSquare, Chess.NO_STONE);
			setStone(Chess.C1, Chess.WHITE_KING);
			setStone(Chess.D1, Chess.WHITE_ROOK);
		    }
		    excludeCastles(WHITE_CASTLE);
		} else { // Black's castling
		    if (kingSquare < rookSquare) { // a black short castle
			setStone(kingSquare, Chess.NO_STONE);
			setStone(rookSquare, Chess.NO_STONE);
			setStone(Chess.G8, Chess.BLACK_KING);
			setStone(Chess.F8, Chess.BLACK_ROOK);
		    } else { // a black long castle
			setStone(kingSquare, Chess.NO_STONE);
			setStone(rookSquare, Chess.NO_STONE);
			setStone(Chess.C8, Chess.BLACK_KING);
			setStone(Chess.D8, Chess.BLACK_ROOK);
		    }
		    excludeCastles(BLACK_CASTLE);
		}
	    } else {
		int sqiFrom = Move.getFromSqi(move);
		int sqiTo = Move.getToSqi(move);

		long bbFrom = ofSquare(sqiFrom);
		long bbTo = ofSquare(sqiTo);
		long bbFromTo = bbFrom | bbTo;
		squaresChanged |= bbFromTo;

		if (Move.isCapturing(move)) {
		    if (DEBUG)
			if (isSquareEmpty(sqiTo) && !(getSqiEP() == sqiTo))
			    throw new IllegalMoveException(
				    "Move " + ((getPlyNumber() + 1) / 2 + 1) + ": capture square is empty ("
					    + Integer.toBinaryString(move) + ", " + Move.getString(move) + ")");
		    if (DEBUG)
			if (getColor(sqiTo) == getToPlay())
			    throw new IllegalMoveException(
				    "Move " + ((getPlyNumber() + 1) / 2 + 1) + ": cannot capture own piece ("
					    + Integer.toBinaryString(move) + ", " + Move.getString(move) + ")");
		    if (DEBUG)
			if (getPiece(sqiTo) == Chess.KING)
			    throw new IllegalMoveException(
				    "Move " + ((getPlyNumber() + 1) / 2 + 1) + ": cannot capture the king ("
					    + Integer.toBinaryString(move) + ", " + Move.getString(move) + ")");

		    long notBBTo;
		    if (Move.isEPMove(move)) {
			int pawnSqi = getSqiEP()
				+ (getToPlay() == Chess.WHITE ? -Chess.NUM_OF_COLS : Chess.NUM_OF_COLS);
			notBBTo = ~ofSquare(pawnSqi);
			squaresChanged |= ~notBBTo;
			m_hashCode ^= s_hashMod[pawnSqi][(getToPlay() == Chess.WHITE ? Chess.BLACK_PAWN
				: Chess.WHITE_PAWN) - Chess.MIN_STONE];
		    } else {
			notBBTo = ~bbTo;
			// int capturedStone =
			// Chess.pieceToStone(ChMove.getCapturedPiece(move),
			// getNotToPlay());
			int capturedStone = getStone(Move.getToSqi(move));
			m_hashCode ^= s_hashMod[sqiTo][capturedStone - Chess.MIN_STONE];
		    }
		    // this.printBoard(notBBTo);
		    // remove all bits -> faster than switching?
		    m_bbWhites &= notBBTo;
		    m_bbBlacks &= notBBTo;
		    m_bbPawns &= notBBTo;
		    m_bbKnights &= notBBTo;
		    m_bbBishops &= notBBTo;
		    m_bbRooks &= notBBTo;
		    // this.printBoard(m_bbWhites);this.printBoard(m_bbBlacks);this.printBoard(m_bbPawns);
		    increaseHalfMoveClock = false;
		}
		if (Move.isPromotion(move)) {
		    // System.out.println("PROMOTION");
		    // System.out.println(move + " " +
		    // ChMove.getBinaryString(move) + " " +
		    // ChMove.getString(move));
		    int promotionStone = Chess.pieceToStone(Move.getPromotionPiece(move), getToPlay());
		    if (getToPlay() == Chess.WHITE) {
			m_bbWhites ^= bbFromTo;
			m_bbPawns ^= bbFrom;
			m_hashCode ^= s_hashMod[sqiFrom][Chess.WHITE_PAWN - Chess.MIN_STONE];
			switch (promotionStone) {
			case Chess.WHITE_KNIGHT:
			    m_bbKnights ^= bbTo;
			    break;
			case Chess.WHITE_BISHOP:
			    m_bbBishops ^= bbTo;
			    break;
			case Chess.WHITE_ROOK:
			    m_bbRooks ^= bbTo;
			    break;
			case Chess.WHITE_QUEEN:
			    m_bbBishops ^= bbTo;
			    m_bbRooks ^= bbTo;
			    break;
			default:
			    throw new IllegalMoveException(
				    "Move " + ((getPlyNumber() + 1) / 2 + 1) + ": illegal promotion stone ("
					    + promotionStone + ", " + Chess.stoneToChar(promotionStone) + ")");
			}
		    } else {
			m_bbBlacks ^= bbFromTo;
			m_bbPawns ^= bbFrom;
			m_hashCode ^= s_hashMod[sqiFrom][Chess.BLACK_PAWN - Chess.MIN_STONE];
			switch (promotionStone) {
			case Chess.BLACK_KNIGHT:
			    m_bbKnights ^= bbTo;
			    break;
			case Chess.BLACK_BISHOP:
			    m_bbBishops ^= bbTo;
			    break;
			case Chess.BLACK_ROOK:
			    m_bbRooks ^= bbTo;
			    break;
			case Chess.BLACK_QUEEN:
			    m_bbBishops ^= bbTo;
			    m_bbRooks ^= bbTo;
			    break;
			default:
			    System.out.println("Position::setMove: IllegalMoveException at " + this);
			    String message = "Move " + ((getPlyNumber() + 1) / 2 + 1) + ": illegal promotion stone ("
				    + promotionStone + ", " + Chess.stoneToChar(promotionStone) + ")";
			    System.out.println(message);
			    throw new IllegalMoveException(message);
			}
		    }
		    m_hashCode ^= s_hashMod[sqiTo][promotionStone - Chess.MIN_STONE];
		    increaseHalfMoveClock = false;
		} else {
		    int stone = getStone(Move.getFromSqi(move));
		    switch (stone) {
		    case Chess.NO_STONE: {
			System.out.println("Position::setMove: IllegalMoveException at " + this);
			String message = "Move " + ((getPlyNumber() + 1) / 2 + 1) + "(" + Move.getString(move)
				+ "): moving stone is non-existent";
			System.out.println(message);
			throw new IllegalMoveException(message);
		    }
		    case Chess.WHITE_KING:
			m_bbWhites ^= bbFromTo;
			m_whiteKing = sqiTo;
			break;
		    case Chess.WHITE_PAWN:
			m_bbWhites ^= bbFromTo;
			m_bbPawns ^= bbFromTo;
			increaseHalfMoveClock = false;
			if (sqiTo - sqiFrom == 2 * Chess.NUM_OF_COLS)
			    sqiEP = sqiTo - Chess.NUM_OF_COLS;
			break;
		    case Chess.WHITE_KNIGHT:
			m_bbWhites ^= bbFromTo;
			m_bbKnights ^= bbFromTo;
			break;
		    case Chess.WHITE_BISHOP:
			m_bbWhites ^= bbFromTo;
			m_bbBishops ^= bbFromTo;
			break;
		    case Chess.WHITE_ROOK:
			m_bbWhites ^= bbFromTo;
			m_bbRooks ^= bbFromTo;
			break;
		    case Chess.WHITE_QUEEN:
			m_bbWhites ^= bbFromTo;
			m_bbBishops ^= bbFromTo;
			m_bbRooks ^= bbFromTo;
			break;
		    case Chess.BLACK_KING:
			m_bbBlacks ^= bbFromTo;
			m_blackKing = sqiTo;
			break;
		    case Chess.BLACK_PAWN:
			m_bbBlacks ^= bbFromTo;
			m_bbPawns ^= bbFromTo;
			increaseHalfMoveClock = false;
			if (sqiFrom - sqiTo == 2 * Chess.NUM_OF_COLS)
			    sqiEP = sqiTo + Chess.NUM_OF_COLS;
			break;
		    case Chess.BLACK_KNIGHT:
			m_bbBlacks ^= bbFromTo;
			m_bbKnights ^= bbFromTo;
			break;
		    case Chess.BLACK_BISHOP:
			m_bbBlacks ^= bbFromTo;
			m_bbBishops ^= bbFromTo;
			break;
		    case Chess.BLACK_ROOK:
			m_bbBlacks ^= bbFromTo;
			m_bbRooks ^= bbFromTo;
			break;
		    case Chess.BLACK_QUEEN:
			m_bbBlacks ^= bbFromTo;
			m_bbBishops ^= bbFromTo;
			m_bbRooks ^= bbFromTo;
			break;
		    }
		    m_hashCode ^= s_hashMod[sqiFrom][stone - Chess.MIN_STONE];
		    m_hashCode ^= s_hashMod[sqiTo][stone - Chess.MIN_STONE];
		}

		/*---------- update castles ----------*/
		int castles = getCastles();
		if (castles != NO_CASTLES) {
		    if (m_variant == Variant.STANDARD) {
			if (sqiFrom == Chess.A1 || sqiTo == Chess.A1) {
			    castles &= ~WHITE_LONG_CASTLE;
			} else if (sqiFrom == Chess.H1 || sqiTo == Chess.H1) {
			    castles &= ~WHITE_SHORT_CASTLE;
			} else if (sqiFrom == Chess.A8 || sqiTo == Chess.A8) {
			    castles &= ~BLACK_LONG_CASTLE;
			} else if (sqiFrom == Chess.H8 || sqiTo == Chess.H8) {
			    castles &= ~BLACK_SHORT_CASTLE;
			} else if (sqiFrom == Chess.E1) {
			    castles &= ~WHITE_CASTLE;
			} else if (sqiFrom == Chess.E8) {
			    castles &= ~BLACK_CASTLE;
			}
		    } else { // Chess960
			if (sqiFrom == m_chess960QueensideRookFile || sqiTo == m_chess960QueensideRookFile) {
			    castles &= ~WHITE_LONG_CASTLE;
			} else if (sqiFrom == m_chess960KingsideRookFile || sqiTo == m_chess960KingsideRookFile) {
			    castles &= ~WHITE_SHORT_CASTLE;
			} else if (sqiFrom == m_chess960QueensideRookFile + Chess.A8
				|| sqiTo == m_chess960QueensideRookFile + Chess.A8) {
			    castles &= ~BLACK_LONG_CASTLE;
			} else if (sqiFrom == m_chess960KingsideRookFile + Chess.A8
				|| sqiTo == m_chess960KingsideRookFile + Chess.A8) {
			    castles &= ~BLACK_SHORT_CASTLE;
			} else if (sqiFrom == m_chess960KingFile) {
			    castles &= ~WHITE_CASTLE;
			} else if (sqiFrom == m_chess960KingFile + Chess.A8) {
			    castles &= ~BLACK_CASTLE;
			}
		    }
		    setCastles(castles);
		}
	    }
	}

	/*---------- update to-play, ply number ----------*/
	incPlyNumber();
	toggleToPlay();

	/*---------- notify listeners ----------*/
	if (m_notifyListeners) {
	    // enable this to be sure that changes are sent
	    // for (int i=0; i<Chess.NUM_OF_SQUARES; i++) fireSquareChanged(i);

	    while (squaresChanged != 0L) {
		int sqi = getFirstSqi(squaresChanged);
		fireSquareChanged(sqi);
		squaresChanged &= squaresChanged - 1;
	    }
	}

	/*---------- update ep square ----------*/
	setSqiEP(sqiEP);

	/*---------- update half move clock ----------*/
	if (increaseHalfMoveClock)
	    incHalfMoveClock();
	else
	    resetHalfMoveClock();

	/*---------- store move in stack ----------*/
	int index = m_moveStackIndex;
	checkMoveStack();
	// if (index >= m_moveStack.length) {
	// short[] newMoveStack = new short[m_moveStack.length * 2];
	// System.arraycopy(m_moveStack, 0, newMoveStack, 0,
	// m_moveStack.length);
	// m_moveStack = newMoveStack;
	//// if (index >= m_moveStack.length) System.out.println("Too big");
	// }
	// if (index < 0 || index >= m_moveStack.length)
	// System.out.println(index + " " + m_plyNumber + " " +
	// m_initialPlyNumber + " " + m_moveStack.length);
	m_moveStack[index] = move;
	m_moveStackIndex++;
    }

    private void checkMoveStack() {
	if (m_moveStackIndex >= m_moveStack.length) {
	    short[] newMoveStack = new short[m_moveStack.length * 2];
	    System.arraycopy(m_moveStack, 0, newMoveStack, 0, m_moveStack.length);
	    m_moveStack = newMoveStack;
	    // if (index >= m_moveStack.length) System.out.println("Too big");
	}
    }

    private void checkBackupStack() {
	if (m_bakIndex + 7 >= m_bakStack.length) {
	    long[] oldBak = m_bakStack;
	    m_bakStack = new long[2 * oldBak.length];
	    System.arraycopy(oldBak, 0, m_bakStack, 0, oldBak.length);
	    // System.out.println(m_bakIndex + " " + m_bakStack.length);
	}
    }

    private long getAllFlags(int changeMask) {
	long allFlags = (((m_flags << 6) | m_whiteKing) << 6) | m_blackKing;
	return (allFlags << 5) | changeMask;
    }

    public void takeBaseline() {
	checkBackupStack();

	m_bakStack[m_bakIndex++] = m_hashCode;
	m_bakStack[m_bakIndex++] = m_bbWhites;
	m_bakStack[m_bakIndex++] = m_bbPawns;
	m_bakStack[m_bakIndex++] = m_bbKnights;
	m_bakStack[m_bakIndex++] = m_bbBishops;
	m_bakStack[m_bakIndex++] = m_bbRooks;

	int changeMask = 0x1F;
	long bakFlags = (((m_flags << 6) | m_whiteKing) << 6) | m_blackKing;
	m_bakStack[m_bakIndex++] = (bakFlags << 5) | changeMask;
	m_bakStack[m_bakIndex] = 0L; // prevent redos

	checkMoveStack();
	m_moveStack[m_moveStackIndex++] = OTHER_CHANGE_MOVE;
    }

    @Override
    public void doMove(short move) throws IllegalMoveException {
	doMoveNoMoveListeners(move);
	if (m_notifyListeners)
	    fireMoveDone(move);
    }

    private void doMoveNoMoveListeners(short move) throws IllegalMoveException {
	if (PROFILE)
	    m_numDoMove++;

	boolean notify = m_notifyPositionChanged;
	m_notifyPositionChanged = false;

	if (!Move.isValid(move))
	    throw new IllegalMoveException(move);

	/*---------- back current state up ----------*/
	checkBackupStack();

	/*---------- take baseline ----------*/
	long bakWhites = m_bbWhites;
	long bakPawns = m_bbPawns;
	long bakKnights = m_bbKnights;
	long bakBishops = m_bbBishops;
	long bakRooks = m_bbRooks;
	long bakFlags = (((m_flags << 6) | m_whiteKing) << 6) | m_blackKing;

	m_bakStack[m_bakIndex++] = m_hashCode;

	/*---------- delete position properties in m_flags ----------*/
	m_flags &= ~(CHECK_MASK << CHECK_SHIFT); // delete isCheck info
	m_flags &= ~(CAN_MOVE_MASK << CAN_MOVE_SHIFT); // delete canMove info

	/*---------- move pieces ----------*/
	try {
	    setMove(move);
	} catch (RuntimeException ex) {
	    throw new IllegalMoveException(move, ex.getMessage());
	}

	/*---------- compare state and push changes ----------*/
	// only push data that have actually changed
	// on average, we need about 3.75 longs per position (instead of 7 if we
	// back up all)
	// (hashCode, flags, 1/2 whites, 1 piece bb, plus sometimes another
	// piece bb for captures, promotions, castles)
	int changeMask = 0;
	if (bakWhites != m_bbWhites) {
	    m_bakStack[m_bakIndex++] = bakWhites;
	    changeMask++;
	}
	changeMask <<= 1;
	if (bakPawns != m_bbPawns) {
	    m_bakStack[m_bakIndex++] = bakPawns;
	    changeMask++;
	}
	changeMask <<= 1;
	if (bakKnights != m_bbKnights) {
	    m_bakStack[m_bakIndex++] = bakKnights;
	    changeMask++;
	}
	changeMask <<= 1;
	if (bakBishops != m_bbBishops) {
	    m_bakStack[m_bakIndex++] = bakBishops;
	    changeMask++;
	}
	changeMask <<= 1;
	if (bakRooks != m_bbRooks) {
	    m_bakStack[m_bakIndex++] = bakRooks;
	    changeMask++;
	}
	m_bakStack[m_bakIndex++] = (bakFlags << 5) | changeMask;
	m_bakStack[m_bakIndex] = 0L;

	m_notifyPositionChanged = notify;

	if (PROFILE)
	    m_numLongsBackuped += numOfBitsSet(changeMask) + 2;

	if (DEBUG)
	    System.out.println("I did a move " + Move.getString(move));
    }

    @Override
    public boolean canUndoMove() {
	return m_bakIndex > 0;
    }

    @Override
    public boolean undoMove() {
	boolean res = undoMoveNoMoveListeners();
	if (res) {
	    if (m_notifyListeners)
		fireMoveUndone();
	}
	return res;
    }

    private boolean undoMoveNoMoveListeners() {
	if (PROFILE)
	    m_numUndoMove++;

	boolean notify = m_notifyPositionChanged;
	m_notifyPositionChanged = false;

	if (m_bakIndex > 0) {
	    long bbWhites = m_bbWhites, bbBlacks = m_bbBlacks;
	    int sqiEP = getSqiEP();
	    int castles = getCastles();

	    /*---------- reset pieces ----------*/
	    long allFlags = m_bakStack[--m_bakIndex];
	    int changeMask = (int) (allFlags & 0x1F);
	    allFlags >>>= 5;

	    int newChangeMask = 0;
	    if ((changeMask & 1) != 0) {
		m_bakStack[m_bakIndex] = m_bbRooks;
		m_bbRooks = m_bakStack[--m_bakIndex];
		newChangeMask++;
	    }
	    changeMask >>>= 1;
	    newChangeMask <<= 1;
	    if ((changeMask & 1) != 0) {
		m_bakStack[m_bakIndex] = m_bbBishops;
		m_bbBishops = m_bakStack[--m_bakIndex];
		newChangeMask++;
	    }
	    changeMask >>>= 1;
	    newChangeMask <<= 1;
	    if ((changeMask & 1) != 0) {
		m_bakStack[m_bakIndex] = m_bbKnights;
		m_bbKnights = m_bakStack[--m_bakIndex];
		newChangeMask++;
	    }
	    changeMask >>>= 1;
	    newChangeMask <<= 1;
	    if ((changeMask & 1) != 0) {
		m_bakStack[m_bakIndex] = m_bbPawns;
		m_bbPawns = m_bakStack[--m_bakIndex];
		newChangeMask++;
	    }
	    changeMask >>>= 1;
	    newChangeMask <<= 1;
	    if ((changeMask & 1) != 0) {
		m_bakStack[m_bakIndex] = m_bbWhites;
		m_bbWhites = m_bakStack[--m_bakIndex];
		newChangeMask++;
	    }
	    m_bakStack[m_bakIndex] = m_hashCode;
	    m_hashCode = m_bakStack[--m_bakIndex];
	    m_bakStack[m_bakIndex] = getAllFlags(newChangeMask);

	    m_blackKing = (int) (allFlags & 0x3F);
	    allFlags >>>= 6;
	    m_whiteKing = (int) (allFlags & 0x3F);
	    allFlags >>>= 6;
	    m_flags = allFlags;
	    m_bbBlacks = ((1L << m_blackKing) | m_bbPawns | m_bbKnights | m_bbBishops | m_bbRooks) & (~m_bbWhites);

	    m_moveStackIndex--;

	    if (DEBUG)
		System.out.println("Last move undone.");

	    // ---------- notify listeners ----------
	    if (m_notifyListeners) {
		long squaresChanged = (bbWhites ^ m_bbWhites) | (bbBlacks ^ m_bbBlacks);
		while (squaresChanged != 0L) {
		    int sqi = getFirstSqi(squaresChanged);
		    fireSquareChanged(sqi);
		    squaresChanged &= squaresChanged - 1;
		}
		if (getSqiEP() != sqiEP)
		    fireSqiEPChanged();
		if (getCastles() != castles)
		    fireCastlesChanged();
		fireHalfMoveClockChanged();
		fireToPlayChanged();
	    }

	    m_notifyPositionChanged = notify;
	    return true;

	} else {
	    m_notifyPositionChanged = notify;
	    return false;
	}
    }

    @Override
    public boolean canRedoMove() {
	return m_bakIndex < m_bakStack.length && m_bakStack[m_bakIndex] != 0;
    }

    @Override
    public boolean redoMove() {
	boolean res = redoMoveNoMoveListeners();
	if (m_notifyListeners)
	    fireMoveDone(getLastShortMove());
	return res;
    }

    private boolean redoMoveNoMoveListeners() {
	// if (PROFILE) m_numRedoMove++;

	boolean notify = m_notifyPositionChanged;
	m_notifyPositionChanged = false;

	if (canRedoMove()) {
	    long bbWhites = m_bbWhites, bbBlacks = m_bbBlacks;
	    int sqiEP = getSqiEP();
	    int castles = getCastles();

	    /*---------- reset pieces ----------*/
	    long allFlags = m_bakStack[m_bakIndex];
	    int changeMask = (int) (allFlags & 0x1F);
	    allFlags >>>= 5;

	    int newChangeMask = 0;
	    m_bakStack[m_bakIndex] = m_hashCode;
	    m_hashCode = m_bakStack[++m_bakIndex];
	    if ((changeMask & 1) != 0) {
		m_bakStack[m_bakIndex] = m_bbWhites;
		m_bbWhites = m_bakStack[++m_bakIndex];
		newChangeMask++;
	    }
	    changeMask >>>= 1;
	    newChangeMask <<= 1;
	    if ((changeMask & 1) != 0) {
		m_bakStack[m_bakIndex] = m_bbPawns;
		m_bbPawns = m_bakStack[++m_bakIndex];
		newChangeMask++;
	    }
	    changeMask >>>= 1;
	    newChangeMask <<= 1;
	    if ((changeMask & 1) != 0) {
		m_bakStack[m_bakIndex] = m_bbKnights;
		m_bbKnights = m_bakStack[++m_bakIndex];
		newChangeMask++;
	    }
	    changeMask >>>= 1;
	    newChangeMask <<= 1;
	    if ((changeMask & 1) != 0) {
		m_bakStack[m_bakIndex] = m_bbBishops;
		m_bbBishops = m_bakStack[++m_bakIndex];
		newChangeMask++;
	    }
	    changeMask >>>= 1;
	    newChangeMask <<= 1;
	    if ((changeMask & 1) != 0) {
		m_bakStack[m_bakIndex] = m_bbRooks;
		m_bbRooks = m_bakStack[++m_bakIndex];
		newChangeMask++;
	    }
	    m_bakStack[m_bakIndex++] = getAllFlags(newChangeMask);

	    m_blackKing = (int) (allFlags & 0x3F);
	    allFlags >>>= 6;
	    m_whiteKing = (int) (allFlags & 0x3F);
	    allFlags >>>= 6;
	    m_flags = allFlags;
	    m_bbBlacks = ((1L << m_blackKing) | m_bbPawns | m_bbKnights | m_bbBishops | m_bbRooks) & (~m_bbWhites);

	    m_moveStackIndex++;

	    if (DEBUG)
		System.out.println("Last move redone.");

	    /*---------- notify listeners ----------*/
	    if (m_notifyListeners) {
		long squaresChanged = (bbWhites ^ m_bbWhites) | (bbBlacks ^ m_bbBlacks);
		while (squaresChanged != 0L) {
		    int sqi = getFirstSqi(squaresChanged);
		    fireSquareChanged(sqi);
		    squaresChanged &= squaresChanged - 1;
		}
		if (getSqiEP() != sqiEP)
		    fireSqiEPChanged();
		if (getCastles() != castles)
		    fireCastlesChanged();
		fireHalfMoveClockChanged();
		fireToPlayChanged();
	    }

	    m_notifyPositionChanged = notify;
	    return true;

	} else {
	    m_notifyPositionChanged = notify;
	    return false;
	}
    }

    /*
     * =========================================================================
     */

    @Override
    public Validity isValid() {
	Validity val = super.isValid();
	if (val != Validity.IS_VALID)
	    return val;

	/*---------- king of toPlay must not be attacked ----------*/
	if (!checkKingOfToPlay()) {
	    return Validity.WRONG_KING_ATTACKED;
	}
	return Validity.IS_VALID;
    }

    public boolean checkKingOfToPlay() {
	int kingSquare = (getToPlay() == Chess.WHITE ? m_blackKing : m_whiteKing);
	if (isAttacked(kingSquare, getToPlay(), 0L))
	    return false; // =====>

	return true;
    }

    @Override
    public void internalValidate() throws IllegalPositionException {
	super.internalValidate();

	if (m_whiteKing < 0 || m_whiteKing >= Chess.NUM_OF_SQUARES)
	    throw new IllegalPositionException("White king square illegal: " + m_whiteKing);
	if (m_blackKing < 0 || m_blackKing >= Chess.NUM_OF_SQUARES)
	    throw new IllegalPositionException("White king square illegal: " + m_blackKing);

	int kingSquare = (getToPlay() == Chess.WHITE ? m_blackKing : m_whiteKing);
	if (isAttacked(kingSquare, getToPlay(), 0L))
	    throw new IllegalPositionException(
		    "King " + Chess.sqiToStr(kingSquare) + " is in check without having the move.");

	if (super.getHashCode() != getHashCode()) {
	    System.out.println("Wrong hash code " + getHashCode() + " should be " + super.getHashCode());
	    // ChBitBoard.printBoard(getHashCode()); System.out.println();
	    // ChBitBoard.printBoard(super.getHashCode());
	    long diff = getHashCode() - super.getHashCode();
	    System.out.println("Difference " + diff);
	    for (int i = 0; i < Chess.NUM_OF_SQUARES; i++) {
		for (int j = 0; j < s_hashMod[i].length; j++) {
		    if (s_hashMod[i][j] == diff) {
			System.out.println("Diff is sqi=" + i + " stone=" + (j - Chess.MIN_STONE));
		    }
		}
	    }
	    for (int i = 0; i < 16; i++) {
		if (s_hashCastleMod[i] == diff) {
		    System.out.println("Diff is castle " + i);
		}
	    }
	    for (int i = 0; i < 8; i++) {
		if (s_hashEPMod[i] == diff) {
		    System.out.println("Diff is sqiEP " + i);
		}
	    }
	    System.out.println("Position.validate: " + FEN.getFEN(this));
	    System.out.println("Position.validate: " + FEN.getFEN(new LightWeightPosition(this)));
	    throw new IllegalPositionException("Wrong hash code " + getHashCode() + " should be " + super.getHashCode()
		    + " difference " + (getHashCode() - super.getHashCode()));
	}
    }

    /*
     * =========================================================================
     */

    public final boolean isCheck() {
	if (PROFILE)
	    m_numIsCheck++;

	int cacheInfo = (int) (m_flags >> CHECK_SHIFT) & CHECK_MASK;
	if (cacheInfo == FLAG_YES) {
	    return true;
	} else if (cacheInfo == FLAG_NO) {
	    return false;
	} else {
	    boolean isCheck;
	    if (getToPlay() == Chess.WHITE) {
		isCheck = isAttacked(m_whiteKing, Chess.BLACK, 0L);
	    } else {
		isCheck = isAttacked(m_blackKing, Chess.WHITE, 0L);
	    }
	    m_flags &= ~(CHECK_MASK << CHECK_SHIFT);
	    m_flags |= (isCheck ? FLAG_YES : FLAG_NO) << CHECK_SHIFT;
	    return isCheck;
	}
    }

    public boolean isTerminal() {
	return !canMove() || getHalfMoveClock() >= 100;
    }

    public boolean isMate() {
	if (PROFILE)
	    m_numIsMate++;

	return isCheck() && !canMove();
    }

    public boolean isStaleMate() {
	if (PROFILE)
	    m_numIsStaleMate++;

	return !isCheck() && !canMove();
    }

    /*
     * =========================================================================
     */

    @Override
    public short getLastShortMove() {
	return (m_moveStackIndex <= 0 ? Move.NO_MOVE : m_moveStack[m_moveStackIndex - 1]);
    }

    @Override
    public Move getLastMove() {
	if (m_moveStackIndex == 0)
	    return null; // =====>
	short move = m_moveStack[m_moveStackIndex - 1];
	boolean wasWhiteMove = (getToPlay() == Chess.BLACK);
	if (Move.isCastle(move)) {
	    return Move.createCastle(move, isCheck(), isMate(), wasWhiteMove); // ======>
	} else {
	    int from = Move.getFromSqi(move);
	    int to = Move.getToSqi(move);
	    int piece = (Move.isPromotion(move) ? Chess.PAWN : getPiece(to));
	    boolean isCapturing = Move.isCapturing(move);

	    int colFrom = Chess.NO_COL;
	    int rowFrom = Chess.NO_ROW;
	    if (piece == Chess.PAWN) {
		if (isCapturing)
		    colFrom = Chess.sqiToCol(from);
		return new Move(move, Chess.PAWN, colFrom, rowFrom, isCheck(), isMate(), wasWhiteMove);
	    } else {
		try {
		    return getLastMovePiece(move);
		} catch (IllegalMoveException ex) {
		    return null;
		}
	    }
	}
    }

    public String getLastMoveAsSanWithNumber() {
	int plies = getPlyNumber();
	if (plies > 0) {
	    Move move = getLastMove();
	    if (move == null) { // this can happen, if the start position is given by FEN
		return "";
	    }
	    if (plies % 2 == 1) {
		return String.valueOf((plies + 1) / 2) + ". " + move.getSAN();
	    } else {
		return String.valueOf((plies + 1) / 2) + "... " + move.getSAN();
	    }
	} else {
	    return "";
	}
    }

    private int getFromSqi(int piece, int colFrom, int rowFrom, int to) {
	long bb = getBitBoard(Chess.pieceToStone(piece, getToPlay()));
	if (colFrom != Chess.NO_COL) {
	    bb &= ofCol(colFrom);
	}
	if (rowFrom != Chess.NO_ROW) {
	    bb &= ofRow(rowFrom);
	}

	while (bb != 0L) {
	    int from = getFirstSqi(bb);
	    if (DEBUG)
		System.out.print("  trying from: " + from);
	    int pinnedDir = getPinnedDirection(from, getToPlay());
	    if (attacks(from, to) && (pinnedDir == NO_DIR || areDirectionsParallel(pinnedDir, DIR[from][to]))) {
		if (DEBUG)
		    System.out.println(" ok");
		return from;
	    }
	    bb &= bb - 1;
	}
	return Chess.NO_SQUARE;
    }

    public short getPawnMove(int colFrom, int to, int promoPiece) {
	if (to == getSqiEP()) {
	    int from = Chess.coorToSqi(colFrom, getToPlay() == Chess.WHITE ? 4 : 3);
	    return Move.getEPMove(from, to);
	} else if (colFrom == Chess.NO_COL) {
	    int delta = ((getToPlay() == Chess.WHITE) ? Chess.NUM_OF_COLS : -Chess.NUM_OF_COLS);
	    int from = !isSquareEmpty(to - delta) ? to - delta : to - 2 * delta;
	    return Move.getPawnMove(from, to, false, promoPiece);
	} else {
	    int from = Chess.coorToSqi(colFrom, Chess.sqiToRow(to) + (getToPlay() == Chess.WHITE ? -1 : 1));
	    return Move.getPawnMove(from, to, true, promoPiece);
	}
    }

    public short getPieceMove(int piece, int colFrom, int rowFrom, int to) {
	return Move.getRegularMove(getFromSqi(piece, colFrom, rowFrom, to), to, !isSquareEmpty(to));
    }

    /*
     * TN: The following method is a part of Position::getLastMove and it shall be
     * used nowhere else. So far it is not clear why undoMove() and everything until
     * redoMoveNoMoveListeners is necessary. However, if these operations are
     * missing the build Move is invalid because of a NO_PIECE (and probably more
     * problems).
     * 
     * One might think that this undo/redo is a performance issue, but even after
     * more than one million calls within a real application, less than half a
     * second was spend here.
     */
    private Move getLastMovePiece(short move) throws IllegalMoveException {
	if (!Move.isValid(move))
	    throw new IllegalMoveException(move);

	boolean notify = m_notifyListeners;
	m_notifyListeners = false;
	undoMove();

	int from = Move.getFromSqi(move);
	int to = Move.getToSqi(move);
	boolean isCapturing = Move.isCapturing(move);
	int stone = getStone(from);

	int colFrom = Chess.NO_COL;
	int rowFrom = Chess.NO_ROW;

	long bb = getBitBoard(stone) & getDirectAttackers(to, getToPlay(), false) & ~ofSquare(from);
	if (!isCapturing) {
	    bb &= (~m_bbPawns);
	}
	if (bb != 0L) {
	    if ((bb & ofCol(Chess.sqiToCol(from))) == 0L) {
		colFrom = Chess.sqiToCol(from);
	    } else if ((bb & ofRow(Chess.sqiToRow(from))) == 0L) {
		rowFrom = Chess.sqiToRow(from);
	    } else {
		colFrom = Chess.sqiToCol(from);
		rowFrom = Chess.sqiToRow(from);
	    }
	}

	redoMoveNoMoveListeners(); // TN: doMoveNoMoveListeners(move); is fine, too.
	Move m = new Move(move, Chess.stoneToPiece(stone), colFrom, rowFrom, isCheck(), isMate(),
		getToPlay() == Chess.BLACK);
	m_notifyListeners = notify;
	return m;
    }

    /*
     * =========================================================================
     */

    /**
     * Returns the direction in which a piece on <code>sqi</code> is pinned in front
     * of the king of <code>color</code>. Returns <code>NO_DIR</code> if piece is
     * not pinned.
     *
     * @param sqi   the square for which the pinned direction should be computed. It
     *              is not required that there is a piece on the square
     * @param color of king with respect to which the pinned direction is computed
     **/
    private int getPinnedDirection(int sqi, int color) {
	if (PROFILE)
	    m_numGetPinnedDirection++;

	int kingSqi = (color == Chess.WHITE ? m_whiteKing : m_blackKing);
	long bbSqi = ofSquare(sqi);

	if ((QUEEN_ATTACKS[kingSqi] & bbSqi) == 0L)
	    return NO_DIR; // =====>

	int kingDir = DIR[kingSqi][sqi];
	long kingDirRim = RIM_BOARD[kingDir];
	if ((kingDirRim & bbSqi) != 0L)
	    return NO_DIR; // =====> nothing behind piece

	long bbTarget;
	if (isDiagonal(kingDir)) {
	    bbTarget = BISHOP_ATTACKS[kingSqi] & m_bbBishops & (color == Chess.WHITE ? m_bbBlacks : m_bbWhites);
	} else {
	    bbTarget = ROOK_ATTACKS[kingSqi] & m_bbRooks & (color == Chess.WHITE ? m_bbBlacks : m_bbWhites);
	}
	if (bbTarget == 0L)
	    return NO_DIR; // =====>

	long bbAllPieces = m_bbWhites | m_bbBlacks;
	if ((SQUARES_BETWEEN[kingSqi][sqi] & bbAllPieces) != 0L)
	    return NO_DIR; // =====>

	// System.out.println("now checking behind sqi");
	long bb = bbSqi;
	int vector = DIR_SHIFT[kingDir];
	do {
	    // bb not on rim checked above -> can increment without test
	    if (vector < 0)
		bb >>>= -vector;
	    else
		bb <<= vector;
	    // ChBitBoard.printBoard(bb);
	    if ((bbTarget & bb) != 0L)
		return kingDir; // =====>
	    if ((bbAllPieces & bb) != 0L)
		return NO_DIR; // =====>
	} while ((kingDirRim & bb) == 0L);
	return NO_DIR;
    }

    private static int sign(int i) {
	return Integer.compare(i, 0);
    }

    private boolean attacks(int from, int to) {
	// BS: replace by is attacked
	int piece = getPiece(from);
	long bbTo = ofSquare(to);
	switch (piece) {
	case Chess.NO_PIECE:
	    return false;
	case Chess.PAWN:
	    if (getToPlay() == Chess.WHITE)
		return (WHITE_PAWN_ATTACKS[from] & bbTo) != 0;
	    else
		return (BLACK_PAWN_ATTACKS[from] & bbTo) != 0;
	case Chess.KNIGHT:
	    return (KNIGHT_ATTACKS[from] & bbTo) != 0;
	case Chess.KING:
	    return (KING_ATTACKS[from] & bbTo) != 0;
	case Chess.BISHOP:
	case Chess.ROOK:
	case Chess.QUEEN:
	    if (piece == Chess.BISHOP && (BISHOP_ATTACKS[from] & bbTo) == 0)
		return false; // =====>
	    if (piece == Chess.ROOK && (ROOK_ATTACKS[from] & bbTo) == 0)
		return false; // =====>
	    if (piece == Chess.QUEEN && (QUEEN_ATTACKS[from] & bbTo) == 0)
		return false; // =====>
	    long bbFrom = ofSquare(from);
	    int vector = DIR_SHIFT[DIR[from][to]];
	    if (vector < 0)
		bbFrom >>>= -vector;
	    else
		bbFrom <<= vector;
	    while (bbFrom != bbTo) {
		if (((m_bbWhites | m_bbBlacks) & bbFrom) != 0)
		    return false; // =====>
		if (vector < 0)
		    bbFrom >>>= -vector;
		else
		    bbFrom <<= vector;
	    }
	    return true; // =====>
	default:
	    throw new RuntimeException("Illegal piece: " + piece);
	}
    }

    /*
     * =========================================================================
     */

    private boolean isAttacked(int sqi, int attacker, long bbExclude) {
	if (PROFILE)
	    m_numIsAttacked++;

	// only to print sqi, otherwise not needed
	if (sqi < 0 || sqi > 63)
	    throw new IllegalArgumentException("Illegal sqi: " + sqi);

	long bbAttackerPieces = (attacker == Chess.WHITE ? m_bbWhites : m_bbBlacks) & (~bbExclude);
	long bbAllPieces = (m_bbWhites | m_bbBlacks) & (~bbExclude);

	/*---------- knights ----------*/
	if ((KNIGHT_ATTACKS[sqi] & bbAttackerPieces & m_bbKnights) != 0)
	    return true; // =====>

	/*---------- sliding pieces ----------*/
	long bbTargets = ((BISHOP_ATTACKS[sqi] & m_bbBishops) | (ROOK_ATTACKS[sqi] & m_bbRooks)) & bbAttackerPieces;
	while (bbTargets != 0L) {
	    int from = getFirstSqi(bbTargets);
	    // if (SQUARES_BETWEEN[from][sqi] == 0L) System.out.println("SQB is
	    // 0");
	    if ((SQUARES_BETWEEN[from][sqi] & bbAllPieces) == 0L)
		return true; // =====>
	    bbTargets &= bbTargets - 1;
	}

	/*---------- king & pawns ----------*/
	if (attacker == Chess.WHITE) {
	    // inverse -> black_pawn_attacks
	    if ((BLACK_PAWN_ATTACKS[sqi] & bbAttackerPieces & m_bbPawns) != 0)
		return true; // =====>
	    if ((KING_ATTACKS[sqi] & ofSquare(m_whiteKing) & (~bbExclude)) != 0)
		return true; // =====>
	} else {
	    if ((WHITE_PAWN_ATTACKS[sqi] & bbAttackerPieces & m_bbPawns) != 0)
		return true; // =====>
	    if ((KING_ATTACKS[sqi] & ofSquare(m_blackKing) & (~bbExclude)) != 0)
		return true; // =====>
	}

	return false;
    }

    private long getDirectAttackers(int sqi, int color, boolean includeInbetweenSquares) {
	if (PROFILE)
	    m_numDirectAttackers++;

	long attackers = 0L;
	long bbAttackerPieces = (color == Chess.WHITE ? m_bbWhites : m_bbBlacks);
	long bbAllPieces = m_bbWhites | m_bbBlacks;

	/*---------- knights ----------*/
	attackers |= KNIGHT_ATTACKS[sqi] & bbAttackerPieces & m_bbKnights;

	/*---------- sliding pieces ----------*/
	long bbTargets = ((BISHOP_ATTACKS[sqi] & m_bbBishops) | (ROOK_ATTACKS[sqi] & m_bbRooks)) & bbAttackerPieces;
	while (bbTargets != 0L) {
	    int from = getFirstSqi(bbTargets);
	    long squaresInBetween = SQUARES_BETWEEN[from][sqi];
	    if ((squaresInBetween & bbAllPieces) == 0L) {
		attackers |= ofSquare(from);
		if (includeInbetweenSquares)
		    attackers |= squaresInBetween;
	    }
	    bbTargets &= bbTargets - 1;
	}

	/*---------- pawns & king ----------*/
	if (color == Chess.WHITE) {
	    // inverse -> black_pawn_attacks
	    attackers |= BLACK_PAWN_ATTACKS[sqi] & bbAttackerPieces & m_bbPawns;
	    attackers |= KING_ATTACKS[sqi] & ofSquare(m_whiteKing);
	    if (sqi == getSqiEP()) {
		attackers |= BLACK_PAWN_ATTACKS[sqi - Chess.NUM_OF_COLS] & bbAttackerPieces & m_bbPawns;
	    }
	} else {
	    attackers |= WHITE_PAWN_ATTACKS[sqi] & bbAttackerPieces & m_bbPawns;
	    attackers |= KING_ATTACKS[sqi] & ofSquare(m_blackKing);
	    if (sqi == getSqiEP()) {
		attackers |= WHITE_PAWN_ATTACKS[sqi + Chess.NUM_OF_COLS] & bbAttackerPieces & m_bbPawns;
	    }
	}

	return attackers;
    }

    private long getAllAttackers(int sqi, int color) {
	if (PROFILE)
	    m_numGetAllAttackers++;

	long attackers = 0L;
	long bbAttackerPieces = (color == Chess.WHITE ? m_bbWhites : m_bbBlacks);
	long bbAllPieces = m_bbWhites | m_bbBlacks;

	/*---------- knights ----------*/
	attackers |= KNIGHT_ATTACKS[sqi] & bbAttackerPieces & m_bbKnights;

	/*---------- sliding pieces ----------*/
	long bbTargets = BISHOP_ATTACKS[sqi] & m_bbBishops & bbAttackerPieces;
	long bb = bbTargets;
	while (bb != 0L) {
	    int from = getFirstSqi(bb);
	    if ((SQUARES_BETWEEN[from][sqi] & bbAllPieces & (~bbTargets)) == 0L) {
		attackers |= ofSquare(from);
	    }
	    bb &= bb - 1;
	}

	bbTargets = ROOK_ATTACKS[sqi] & m_bbRooks & bbAttackerPieces;
	bb = bbTargets;
	while (bb != 0L) {
	    int from = getFirstSqi(bb);
	    if ((SQUARES_BETWEEN[from][sqi] & bbAllPieces & (~bbTargets)) == 0L) {
		attackers |= ofSquare(from);
	    }
	    bb &= bb - 1;
	}

	/*---------- pawns & king ----------*/
	if (color == Chess.WHITE) {
	    // inverse -> black_pawn_attacks
	    attackers |= BLACK_PAWN_ATTACKS[sqi] & bbAttackerPieces & m_bbPawns;
	    attackers |= KING_ATTACKS[sqi] & ofSquare(m_whiteKing);
	    if (sqi == getSqiEP()) {
		attackers |= BLACK_PAWN_ATTACKS[sqi - Chess.NUM_OF_COLS] & bbAttackerPieces & m_bbPawns;
	    }
	} else {
	    attackers |= WHITE_PAWN_ATTACKS[sqi] & bbAttackerPieces & m_bbPawns;
	    attackers |= KING_ATTACKS[sqi] & ofSquare(m_blackKing);
	    if (sqi == getSqiEP()) {
		attackers |= WHITE_PAWN_ATTACKS[sqi + Chess.NUM_OF_COLS] & bbAttackerPieces & m_bbPawns;
	    }
	}

	return attackers;
    }

    private int getAllKnightMoves(int moveIndex, long bbTargets) {
	if (bbTargets == 0L)
	    return moveIndex;

	long bbToPlay = (getToPlay() == Chess.WHITE ? m_bbWhites : m_bbBlacks);

	/*---------- knights moves ----------*/
	long bbPieces = m_bbKnights & bbToPlay;
	while (bbPieces != 0L) {
	    int from = getFirstSqi(bbPieces);
	    if (getPinnedDirection(from, getToPlay()) == NO_DIR) {
		long destSquares = KNIGHT_ATTACKS[from] & (~bbToPlay) & bbTargets;
		while (destSquares != 0L) {
		    if (moveIndex == -1)
			return 1; // =====>
		    int to = getFirstSqi(destSquares);
		    m_moves[moveIndex++] = Move.getRegularMove(from, to, !isSquareEmpty(to));
		    destSquares &= destSquares - 1;
		}
	    }
	    bbPieces &= bbPieces - 1;
	}
	return moveIndex;
    }

    // TN: seemingly a method for bishop, queen and rook moves
    private int getAllSlidingMoves(int moveIndex, long bbTargets, long bbPieces, int piece) {
	if (bbTargets == 0L)
	    return moveIndex;

	long bbToPlay = (getToPlay() == Chess.WHITE ? m_bbWhites : m_bbBlacks);
	long bbNotToPlay = (getToPlay() == Chess.WHITE ? m_bbBlacks : m_bbWhites);

	int dirStep = (piece == Chess.QUEEN ? 1 : 2);
	int startDir = (piece == Chess.ROOK ? S : SW);

	while (bbPieces != 0L) {
	    int from = getFirstSqi(bbPieces);
	    int pinnedDir = getPinnedDirection(from, getToPlay());
	    for (int dir = startDir; dir < NUM_OF_DIRS; dir += dirStep) {
		if ((RAY[from][dir] & bbTargets) != 0L) {
		    if (pinnedDir == NO_DIR || areDirectionsParallel(dir, pinnedDir)) {
			int dirShift = DIR_SHIFT[dir];
			long bb = ofSquare(from);
			int to = from;
			long rimBoard = RIM_BOARD[dir];
			while ((bb & rimBoard) == 0L) {
			    if (dirShift < 0)
				bb >>>= -dirShift;
			    else
				bb <<= dirShift;
			    to += dirShift;
			    // ChBitBoard.printBoard(bb);
			    if ((bb & bbToPlay) != 0L)
				break;
			    // System.out.println("move:"+ Chess.sqiToStr(from)
			    // + "-" + Chess.sqiToStr(to));
			    if ((bb & bbTargets) != 0L) {
				if (moveIndex == -1)
				    return 1; // =====>
				if ((bb & bbNotToPlay) == 0L) {
				    m_moves[moveIndex++] = Move.getRegularMove(from, to, false);
				} else {
				    m_moves[moveIndex++] = Move.getRegularMove(from, to, true);
				    break;
				}
			    } else if ((bb & bbNotToPlay) != 0)
				break;
			}
		    }
		}
	    }
	    bbPieces &= bbPieces - 1;
	}
	return moveIndex;
    }

    private int getAllKingMoves(int moveIndex, long bbTargets, boolean withCastles) {
	if (bbTargets == 0L)
	    return moveIndex;

	long bbToPlay = (getToPlay() == Chess.WHITE ? m_bbWhites : m_bbBlacks);
	long bbAllPieces = m_bbWhites | m_bbBlacks;

	/*---------- regular king moves ----------*/
	int from = (getToPlay() == Chess.WHITE ? m_whiteKing : m_blackKing);
	long bbFrom = ofSquare(from);
	long destSquares = KING_ATTACKS[from] & (~bbToPlay) & bbTargets;
	while (destSquares != 0L) {
	    int to = getFirstSqi(destSquares);
	    if (!isAttacked(to, getNotToPlay(), bbFrom)) {
		// System.out.println("move:"+ Chess.sqiToStr(from) + "-" +
		// Chess.sqiToStr(to));
		if (moveIndex == -1)
		    return 1; // =====>
		m_moves[moveIndex++] = Move.getRegularMove(from, to, !isSquareEmpty(to));
	    }
	    destSquares &= destSquares - 1;
	}

	/*---------- castles ----------*/
	if (withCastles) {
	    int castles = getCastles();
	    if (m_variant == Variant.STANDARD) {
		if (getToPlay() == Chess.WHITE) {
		    // don't need to exclude anything for isAttack since other check
		    // would fail in those cases
		    if ((castles & WHITE_SHORT_CASTLE) != 0 && (ofSquare(Chess.G1) & bbTargets) != 0L
			    && (bbAllPieces & WHITE_SHORT_CASTLE_EMPTY_MASK) == 0L
			    && !isAttacked(Chess.F1, Chess.BLACK, 0L) && !isAttacked(Chess.G1, Chess.BLACK, 0L)) {
			if (moveIndex == -1)
			    return 1; // =====>
			m_moves[moveIndex++] = Move.WHITE_SHORT_CASTLE;
		    }
		    if ((castles & WHITE_LONG_CASTLE) != 0 && (ofSquare(Chess.C1) & bbTargets) != 0L
			    && (bbAllPieces & WHITE_LONG_CASTLE_EMPTY_MASK) == 0L
			    && !isAttacked(Chess.D1, Chess.BLACK, 0L) && !isAttacked(Chess.C1, Chess.BLACK, 0L)) {
			if (moveIndex == -1)
			    return 1; // =====>
			m_moves[moveIndex++] = Move.WHITE_LONG_CASTLE;
		    }
		} else {
		    if ((castles & BLACK_SHORT_CASTLE) != 0 && (ofSquare(Chess.G8) & bbTargets) != 0L
			    && (bbAllPieces & BLACK_SHORT_CASTLE_EMPTY_MASK) == 0L
			    && !isAttacked(Chess.F8, Chess.WHITE, 0L) && !isAttacked(Chess.G8, Chess.WHITE, 0L)) {
			if (moveIndex == -1)
			    return 1; // =====>
			m_moves[moveIndex++] = Move.BLACK_SHORT_CASTLE;
		    }
		    if ((castles & BLACK_LONG_CASTLE) != 0 && (ofSquare(Chess.C8) & bbTargets) != 0L
			    && (bbAllPieces & BLACK_LONG_CASTLE_EMPTY_MASK) == 0L
			    && !isAttacked(Chess.D8, Chess.WHITE, 0L) && !isAttacked(Chess.C8, Chess.WHITE, 0L)) {
			if (moveIndex == -1)
			    return 1; // =====>
			m_moves[moveIndex++] = Move.BLACK_LONG_CASTLE;
		    }
		}
	    } else { // Chess960
		if (getToPlay() == Chess.WHITE) {
		    if (checkChess960WhiteShortCastle(bbAllPieces, bbTargets)) {
			if (moveIndex == -1) {
			    return 1;
			}
			m_moves[moveIndex++] = Move.getChess960Castle(Chess.WHITE, m_chess960KingFile,
				m_chess960KingsideRookFile);
		    }
		    if (checkChess960WhiteLongCastle(bbAllPieces, bbTargets)) {
			if (moveIndex == -1) {
			    return 1;
			}
			m_moves[moveIndex++] = Move.getChess960Castle(Chess.WHITE, m_chess960KingFile,
				m_chess960QueensideRookFile);
		    }
		} else {
		    if (checkChess960BlackShortCastle(bbAllPieces, bbTargets)) {
			if (moveIndex == -1) {
			    return 1;
			}
			m_moves[moveIndex++] = Move.getChess960Castle(Chess.BLACK, m_chess960KingFile,
				m_chess960KingsideRookFile);
		    }
		    if (checkChess960BlackLongCastle(bbAllPieces, bbTargets)) {
			if (moveIndex == -1) {
			    return 1;
			}
			m_moves[moveIndex++] = Move.getChess960Castle(Chess.BLACK, m_chess960KingFile,
				m_chess960QueensideRookFile);
		    }
		}
	    }
	}
	return moveIndex;
    }

    private boolean checkChess960KingCastleCondition(long bbAllPieces, int startSquare, int endSquare, int exception,
	    int attacker) {
	for (int square = startSquare; square <= endSquare; ++square) {
	    if (square != exception) {
		if ((bbAllPieces & ofSquare(square)) != 0L || isAttacked(square, attacker, 0L)) {
		    return false;
		}
	    } else {
		if (isAttacked(square, attacker, 0L)) {
		    return false;
		}
	    }
	}
	return true;
    }

    private boolean checkChess960RookCastleCondition(long bbAllPieces, int startSquare, int endSquare, int exception) {
	for (int square = startSquare; square <= endSquare; ++square) {
	    if (square != exception && (bbAllPieces & ofSquare(square)) != 0L) {
		return false;
	    }
	}
	return true;
    }

    private boolean checkChess960WhiteShortCastle(long bbAllPieces, long bbTargets) {
	if ((getCastles() & WHITE_SHORT_CASTLE) == 0 || (ofSquare(Chess.G1) & bbTargets) == 0L) {
	    return false;
	}
	if (!checkChess960KingCastleCondition(bbAllPieces, m_chess960KingFile + 1, Chess.G1, m_chess960KingsideRookFile,
		Chess.BLACK)) {
	    return false;
	}
	if (m_chess960KingsideRookFile < Chess.F1) {
	    if (!checkChess960RookCastleCondition(bbAllPieces, m_chess960KingsideRookFile + 1, Chess.F1,
		    m_chess960KingFile)) {
		return false;
	    }
	} else if (m_chess960KingsideRookFile > Chess.F1) {
	    if (!checkChess960RookCastleCondition(bbAllPieces, Chess.F1, m_chess960KingsideRookFile - 1,
		    m_chess960KingFile)) {
		return false;
	    }
	}
	return true;
    }

    private boolean checkChess960WhiteLongCastle(long bbAllPieces, long bbTargets) {
	if ((getCastles() & WHITE_LONG_CASTLE) == 0 || (ofSquare(Chess.C1) & bbTargets) == 0L) {
	    return false;
	}
	if (m_chess960KingFile < Chess.C1) {
	    if (!checkChess960KingCastleCondition(bbAllPieces, m_chess960KingFile + 1, Chess.C1,
		    m_chess960QueensideRookFile, Chess.BLACK)) {
		return false;
	    }
	} else {
	    if (!checkChess960KingCastleCondition(bbAllPieces, Chess.C1, m_chess960KingFile - 1,
		    m_chess960QueensideRookFile, Chess.BLACK)) {
		return false;
	    }
	}
	if (m_chess960QueensideRookFile < Chess.D1) {
	    if (!checkChess960RookCastleCondition(bbAllPieces, m_chess960QueensideRookFile + 1, Chess.D1,
		    m_chess960KingFile)) {
		return false;
	    }
	} else if (m_chess960QueensideRookFile > Chess.D1) {
	    if (!checkChess960RookCastleCondition(bbAllPieces, Chess.D1, m_chess960QueensideRookFile - 1,
		    m_chess960KingFile)) {
		return false;
	    }
	}
	return true;
    }

    private boolean checkChess960BlackShortCastle(long bbAllPieces, long bbTargets) {
	if ((getCastles() & BLACK_SHORT_CASTLE) == 0 || (ofSquare(Chess.G8) & bbTargets) == 0L) {
	    return false;
	}
	if (!checkChess960KingCastleCondition(bbAllPieces, Chess.A8 + m_chess960KingFile + 1, Chess.A8 + Chess.G1,
		Chess.A8 + m_chess960KingsideRookFile, Chess.WHITE)) {
	    return false;
	}
	if (Chess.A8 + m_chess960KingsideRookFile < Chess.F8) {
	    if (!checkChess960RookCastleCondition(bbAllPieces, Chess.A8 + m_chess960KingsideRookFile + 1, Chess.F8,
		    Chess.A8 + m_chess960KingFile)) {
		return false;
	    }
	} else if (Chess.A8 + m_chess960KingsideRookFile > Chess.F8) {
	    if (!checkChess960RookCastleCondition(bbAllPieces, Chess.F8, Chess.A8 + m_chess960KingsideRookFile - 1,
		    Chess.A8 + m_chess960KingFile)) {
		return false;
	    }
	}
	return true;
    }

    private boolean checkChess960BlackLongCastle(long bbAllPieces, long bbTargets) {
	if ((getCastles() & BLACK_LONG_CASTLE) == 0 || (ofSquare(Chess.C8) & bbTargets) == 0L) {
	    return false;
	}
	if (Chess.A8 + m_chess960KingFile < Chess.C8) {
	    if (!checkChess960KingCastleCondition(bbAllPieces, Chess.A8 + m_chess960KingFile + 1, Chess.C8,
		    Chess.A8 + m_chess960QueensideRookFile, Chess.WHITE)) {
		return false;
	    }
	} else {
	    if (!checkChess960KingCastleCondition(bbAllPieces, Chess.C8, Chess.A8 + m_chess960KingFile - 1,
		    Chess.A8 + m_chess960QueensideRookFile, Chess.WHITE)) {
		return false;
	    }
	}
	if (Chess.A8 + m_chess960QueensideRookFile < Chess.D8) {
	    if (!checkChess960RookCastleCondition(bbAllPieces, Chess.A8 + m_chess960QueensideRookFile + 1, Chess.D8,
		    Chess.A8 + m_chess960KingFile)) {
		return false;
	    }
	} else if (Chess.A8 + m_chess960QueensideRookFile > Chess.D8) {
	    if (!checkChess960RookCastleCondition(bbAllPieces, Chess.D8, Chess.A8 + m_chess960QueensideRookFile - 1,
		    Chess.A8 + m_chess960KingFile)) {
		return false;
	    }
	}
	return true;
    }

    private int getAllPawnMoves(int moveIndex, long bbTargets) {
	if (bbTargets == 0L)
	    return moveIndex;

	long bbToPlay, bbNotToPlay; // , bbAllPieces;
	@SuppressWarnings("unused")
	int thePawn, pawnMoveDir, secondRank, eighthRank;

	if (getToPlay() == Chess.WHITE) {
	    thePawn = Chess.WHITE_PAWN;
	    bbToPlay = m_bbWhites;
	    bbNotToPlay = m_bbBlacks;
	    pawnMoveDir = N;
	    secondRank = 1;
	    eighthRank = 7;
	} else {
	    thePawn = Chess.BLACK_PAWN;
	    bbToPlay = m_bbBlacks;
	    bbNotToPlay = m_bbWhites;
	    pawnMoveDir = S;
	    secondRank = 6;
	    eighthRank = 0;
	}

	// if pawn belonging to ep square is a target, include ep square as
	// target
	int sqiEP = getSqiEP();
	if (getSqiEP() != Chess.NO_SQUARE) {
	    int epPawnSqi = sqiEP + (getToPlay() == Chess.WHITE ? -Chess.NUM_OF_COLS : Chess.NUM_OF_COLS);
	    if ((bbTargets & ofSquare(epPawnSqi)) != 0) {
		bbTargets |= ofSquare(sqiEP); // pawn cannot move on ep square
					      // without capturing (blocked by
					      // ep pawn), so adding it is
					      // safe
		bbNotToPlay |= ofSquare(sqiEP); // to prevent the ep square from
						// being filtered
	    }
	}

	long bbPieces = m_bbPawns & bbToPlay;
	while (bbPieces != 0L) {
	    int from = getFirstSqi(bbPieces);

	    /*---------- pawn move ----------*/
	    int to = from + DIR_SHIFT[pawnMoveDir];
	    int pinnedDir = getPinnedDirection(from, getToPlay());
	    if (isSquareEmpty(to)) {
		if (pinnedDir == NO_DIR || areDirectionsParallel(pinnedDir, pawnMoveDir)) {
		    long bbTo = ofSquare(to);
		    if (Chess.sqiToRow(to) == eighthRank) {
			if ((bbTo & bbTargets) != 0L) {
			    if (moveIndex == -1)
				return 1; // =====>
			    m_moves[moveIndex++] = Move.getPawnMove(from, to, false, Chess.QUEEN);
			    m_moves[moveIndex++] = Move.getPawnMove(from, to, false, Chess.ROOK);
			    m_moves[moveIndex++] = Move.getPawnMove(from, to, false, Chess.BISHOP);
			    m_moves[moveIndex++] = Move.getPawnMove(from, to, false, Chess.KNIGHT);
			}
		    } else {
			if ((bbTo & bbTargets) != 0L) {
			    if (moveIndex == -1)
				return 1; // =====>
			    m_moves[moveIndex++] = Move.getPawnMove(from, to, false, Chess.NO_PIECE);
			}
			if (Chess.sqiToRow(from) == secondRank) {
			    to += DIR_SHIFT[pawnMoveDir];
			    // no need to check is pinned again, since double
			    // steps are always possible
			    // if single steps are
			    if (isSquareEmpty(to) && (ofSquare(to) & bbTargets) != 0L) {
				if (moveIndex == -1)
				    return 1; // =====>
				m_moves[moveIndex++] = Move.getPawnMove(from, to, false, Chess.NO_PIECE);
			    }
			}
		    }
		}
	    }

	    /*---------- pawn capture ----------*/
	    long destSquares = (getToPlay() == Chess.WHITE ? WHITE_PAWN_ATTACKS[from] : BLACK_PAWN_ATTACKS[from])
		    & bbTargets;
	    destSquares &= bbNotToPlay;

	    while (destSquares != 0L) {
		to = getFirstSqi(destSquares);
		int dir = DIR[from][to];
		if (pinnedDir == NO_DIR || dir == NO_DIR || areDirectionsParallel(pinnedDir, dir)) {
		    if (moveIndex == -1)
			return 1; // =====>
		    // int piece = getPiece(to);
		    if (Chess.sqiToRow(to) == eighthRank) {
			m_moves[moveIndex++] = Move.getPawnMove(from, to, true, Chess.QUEEN);
			m_moves[moveIndex++] = Move.getPawnMove(from, to, true, Chess.ROOK);
			m_moves[moveIndex++] = Move.getPawnMove(from, to, true, Chess.BISHOP);
			m_moves[moveIndex++] = Move.getPawnMove(from, to, true, Chess.KNIGHT);
		    } else if (to == sqiEP) {
			m_moves[moveIndex++] = Move.getEPMove(from, to);
		    } else {
			m_moves[moveIndex++] = Move.getPawnMove(from, to, true, Chess.NO_PIECE);
		    }
		}
		destSquares &= destSquares - 1;
	    }
	    bbPieces &= bbPieces - 1;
	}
	return moveIndex;
    }

    @Override
    public short[] getAllMoves() {
	return getAllMoves(~0L, ~0L);
    }

    public short[] getAllReCapturingMoves(short lastMove) {
	if (Move.isValid(lastMove)) {
	    long bbTargets = ofSquare(Move.getToSqi(lastMove));
	    long bbPawnTargets = (getSqiEP() == Chess.NO_SQUARE ? bbTargets : bbTargets | ofSquare(getSqiEP()));
	    return getAllMoves(bbTargets, bbPawnTargets);
	} else {
	    return new short[0];
	}
    }

    public short[] getAllCapturingMoves() {
	long bbTargets = getToPlay() == Chess.WHITE ? m_bbBlacks : m_bbWhites;
	// can include sqiEP safely since no pawn can move on sqi if it is set
	long bbPawnTargets = (getSqiEP() == Chess.NO_SQUARE ? bbTargets : bbTargets | ofSquare(getSqiEP()));
	return getAllMoves(bbTargets, bbPawnTargets);
    }

    public short[] getAllNonCapturingMoves() {
	long bbTargets = getToPlay() == Chess.WHITE ? ~m_bbBlacks : ~m_bbWhites;
	// can exclude sqiEP safely since no pawn can move on sqi if it is set
	long bbPawnTargets = (getSqiEP() == Chess.NO_SQUARE ? bbTargets : bbTargets & (~ofSquare(getSqiEP())));
	return getAllMoves(bbTargets, bbPawnTargets);
    }

    private short[] getAllMoves(long bbTargets, long bbPawnTargets) {
	if (PROFILE)
	    m_numGetAllMoves++;

	if (bbTargets == 0L)
	    return new short[0]; // =====>

	int moveIndex = 0;

	long bbToPlay = (getToPlay() == Chess.WHITE ? m_bbWhites : m_bbBlacks);
	if (isCheck()) {
	    moveIndex = getAllKingMoves(moveIndex, bbTargets, false);
	    long attackers = getDirectAttackers((getToPlay() == Chess.WHITE ? m_whiteKing : m_blackKing),
		    getNotToPlay(), false);
	    // ChBitBoard.printBoard(attackers);
	    if (isExactlyOneBitSet(attackers)) {
		// System.out.println("investigate piece moves");
		attackers = getDirectAttackers((getToPlay() == Chess.WHITE ? m_whiteKing : m_blackKing), getNotToPlay(),
			true);
		bbTargets &= attackers;
		bbPawnTargets &= attackers;
		moveIndex = getAllKnightMoves(moveIndex, bbTargets);
		moveIndex = getAllSlidingMoves(moveIndex, bbTargets, m_bbBishops & (~m_bbRooks) & bbToPlay,
			Chess.BISHOP);
		moveIndex = getAllSlidingMoves(moveIndex, bbTargets, m_bbRooks & (~m_bbBishops) & bbToPlay, Chess.ROOK);
		moveIndex = getAllSlidingMoves(moveIndex, bbTargets, m_bbRooks & m_bbBishops & bbToPlay, Chess.QUEEN);
		// moveIndex = getAllSlidingMoves(moveIndex, bbTargets,
		// m_bbBishops & bbToPlay, SW);
		// moveIndex = getAllSlidingMoves(moveIndex, bbTargets,
		// m_bbRooks & bbToPlay, S);
		moveIndex = getAllPawnMoves(moveIndex, bbPawnTargets);
	    } else { // double check
		     // printBoard(attackers);
	    }
	} else {
	    moveIndex = getAllKnightMoves(moveIndex, bbTargets);
	    moveIndex = getAllSlidingMoves(moveIndex, bbTargets, m_bbBishops & (~m_bbRooks) & bbToPlay, Chess.BISHOP);
	    moveIndex = getAllSlidingMoves(moveIndex, bbTargets, m_bbRooks & (~m_bbBishops) & bbToPlay, Chess.ROOK);
	    moveIndex = getAllSlidingMoves(moveIndex, bbTargets, m_bbRooks & m_bbBishops & bbToPlay, Chess.QUEEN);
	    moveIndex = getAllKingMoves(moveIndex, bbTargets, true);
	    moveIndex = getAllPawnMoves(moveIndex, bbPawnTargets);
	}

	short[] onlyTheMoves = new short[moveIndex];
	System.arraycopy(m_moves, 0, onlyTheMoves, 0, moveIndex);

	return onlyTheMoves;
    }

    public boolean canMove() {
	int cacheInfo = (int) (m_flags >> CAN_MOVE_SHIFT) & CAN_MOVE_MASK;
	if (cacheInfo == FLAG_YES) {
	    return true;
	} else if (cacheInfo == FLAG_NO) {
	    return false;
	} else {
	    boolean canMove = false;
	    long bbToPlay = (getToPlay() == Chess.WHITE ? m_bbWhites : m_bbBlacks);
	    if (isCheck()) {
		// ChBitBoard.printBoard(bbTargets);
		if (getAllKingMoves(-1, ~0L, false) > 0) {
		    canMove = true;
		} else {
		    long attackers = getDirectAttackers((getToPlay() == Chess.WHITE ? m_whiteKing : m_blackKing),
			    getNotToPlay(), false);
		    if (isExactlyOneBitSet(attackers)) {
			attackers = getDirectAttackers((getToPlay() == Chess.WHITE ? m_whiteKing : m_blackKing),
				getNotToPlay(), true);
			canMove = (getAllKnightMoves(-1, attackers) > 0) || (getAllPawnMoves(-1, attackers) > 0)
				|| (getAllSlidingMoves(-1, attackers, m_bbBishops & (~m_bbRooks) & bbToPlay,
					Chess.BISHOP) > 0)
				|| (getAllSlidingMoves(-1, attackers, m_bbRooks & (~m_bbBishops) & bbToPlay,
					Chess.ROOK) > 0)
				|| (getAllSlidingMoves(-1, attackers, m_bbRooks & m_bbBishops & bbToPlay,
					Chess.QUEEN) > 0);
		    }
		}
	    } else {
		long bbTargets = ~0L;
		canMove = (getAllKnightMoves(-1, bbTargets) > 0) || (getAllPawnMoves(-1, bbTargets) > 0)
			|| (getAllSlidingMoves(-1, bbTargets, m_bbBishops & (~m_bbRooks) & bbToPlay, Chess.BISHOP) > 0)
			|| (getAllSlidingMoves(-1, bbTargets, m_bbRooks & (~m_bbBishops) & bbToPlay, Chess.ROOK) > 0)
			|| (getAllSlidingMoves(-1, bbTargets, m_bbRooks & m_bbBishops & bbToPlay, Chess.QUEEN) > 0)
			|| (getAllKingMoves(-1, bbTargets, false) > 0);
		// don't test castling since it cannot be the only move
	    }
	    m_flags &= ~(CAN_MOVE_MASK << CAN_MOVE_SHIFT);
	    m_flags |= (canMove ? FLAG_YES : FLAG_NO) << CAN_MOVE_SHIFT;
	    return canMove;
	}
    }

    /*
     * =========================================================================
     */

    public int getMaterial() {
	int value = 0;
	// if ((m_bbPawns & m_bbWhites) != 0L) System.out.println("not null");
	value += 100 * (numOfBitsSet(m_bbPawns & m_bbWhites) - numOfBitsSet(m_bbPawns & m_bbBlacks));
	value += 300 * (numOfBitsSet(m_bbKnights & m_bbWhites) - numOfBitsSet(m_bbKnights & m_bbBlacks));
	value += 325 * (numOfBitsSet(m_bbBishops & (~m_bbRooks) & m_bbWhites)
		- numOfBitsSet(m_bbBishops & (~m_bbRooks) & m_bbBlacks));
	value += 500 * (numOfBitsSet(m_bbRooks & (~m_bbBishops) & m_bbWhites)
		- numOfBitsSet(m_bbRooks & (~m_bbBishops) & m_bbBlacks));
	value += 900 * (numOfBitsSet(m_bbRooks & m_bbBishops & m_bbWhites)
		- numOfBitsSet(m_bbRooks & m_bbBishops & m_bbBlacks));
	// System.out.println(value);
	return (getToPlay() == Chess.WHITE ? value : -value);
    }

    public double getDomination() {
	int[] SQUARE_IMPORTANCE = { 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 1, 1, 2, 4, 6, 6, 4, 2, 1, 1, 2, 5, 10,
		10, 5, 1, 1, 1, 2, 5, 10, 10, 5, 1, 1, 1, 2, 4, 6, 6, 4, 2, 1, 1, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1,
		1, 1 };

	double value = 0;
	for (int sqi = 0; sqi < Chess.NUM_OF_SQUARES; sqi++) {
	    long bbWhiteAttackers = getAllAttackers(sqi, Chess.WHITE);
	    long bbBlackAttackers = getAllAttackers(sqi, Chess.BLACK);
	    int score = sign(numOfBitsSet(bbWhiteAttackers) - numOfBitsSet(bbBlackAttackers));
	    value += SQUARE_IMPORTANCE[sqi] * score;
	}
	return (getToPlay() == Chess.WHITE ? value : -value);
    }

    public Variant getVariant() {
	return m_variant;
    }

    public void setVariant(Variant variant) {
	m_variant = variant;
    }

    /**
     * A-file corresponds to Chess.A1, b-file to Chess.B1, etc. See also
     * Move::getChess960Castle(..).
     */
    public void setChess960CastlingFiles(int kingFile, int queensideRookFile, int kingsideRookFile) {
	m_chess960KingFile = kingFile % 8;
	m_chess960QueensideRookFile = queensideRookFile % 8;
	m_chess960KingsideRookFile = kingsideRookFile % 8;
    }

    public int getChess960KingFile() {
	return m_chess960KingFile;
    }

    public int getChess960QueensideRookFile() {
	return m_chess960QueensideRookFile;
    }

    public int getChess960KingsideRookFile() {
	return m_chess960KingsideRookFile;
    }

    @Override
    public short getMove(int from, int to, int promoPiece) {
	if (m_variant != Variant.CHESS960) {
	    return super.getMove(from, to, promoPiece);
	} else {
	    // as super.getMove: no validation!
	    if (getColor(from) != getToPlay())
		return Move.ILLEGAL_MOVE;
	    int piece = getPiece(from);
	    if (piece == Chess.PAWN) {
		if (Chess.sqiToCol(from) == Chess.sqiToCol(to)) { // moves forward
		    return Move.getPawnMove(from, to, false, promoPiece);
		} else { // captures
		    if (getSqiEP() != to) {
			return Move.getPawnMove(from, to, true, promoPiece);
		    } else {
			return Move.getEPMove(from, to);
		    }
		}
	    } else if (piece == Chess.KING) {
		int fromStone = getStone(from);
		int toStone = getStone(to);
		if ((fromStone == Chess.WHITE_KING && toStone == Chess.WHITE_ROOK)
			|| (fromStone == Chess.BLACK_KING && toStone == Chess.BLACK_ROOK)) {
		    return Move.getChess960Castle(getToPlay(), from % 8, to % 8);
		}
	    }
	    return Move.getRegularMove(from, to, !isSquareEmpty(to));
	}
    }

    public static String internalCompare(Position pos1, Position pos2) {
	StringBuilder sb = new StringBuilder("Internal compare:").append(System.lineSeparator());

	if (pos1.m_bbWhites != pos2.m_bbWhites) {
	    sb.append("m_bbWhites differ").append(System.lineSeparator());
	}
	if (pos1.m_bbBlacks != pos2.m_bbBlacks) {
	    sb.append("m_bbBlacks differ").append(System.lineSeparator());
	}
	if (pos1.m_bbPawns != pos2.m_bbPawns) {
	    sb.append("m_bbPawns differ").append(System.lineSeparator());
	}
	if (pos1.m_bbKnights != pos2.m_bbKnights) {
	    sb.append("m_bbKnights differ").append(System.lineSeparator());
	}
	if (pos1.m_bbBishops != pos2.m_bbBishops) {
	    sb.append("m_bbBishops differ").append(System.lineSeparator());
	}
	if (pos1.m_bbRooks != pos2.m_bbRooks) {
	    sb.append("m_bbRooks differ").append(System.lineSeparator());
	}
	if (pos1.m_whiteKing != pos2.m_whiteKing) {
	    sb.append("m_whiteKing differ").append(System.lineSeparator());
	}
	if (pos1.m_blackKing != pos2.m_blackKing) {
	    sb.append("m_blackKing differ").append(System.lineSeparator());
	}
	if (pos1.m_flags != pos2.m_flags) {
	    sb.append("m_flags differ").append(System.lineSeparator());
	}
	if (pos1.m_hashCode != pos2.m_hashCode) {
	    sb.append("m_hashCode differ").append(System.lineSeparator());
	}
	// array:
	if (pos1.m_bakStack.length != pos2.m_bakStack.length) {
	    sb.append("m_bakStack lengths differ").append(System.lineSeparator());
	    for (int i = 0; i < Math.min(pos1.m_bakStack.length, pos2.m_bakStack.length); ++i) {
		if (pos1.m_bakStack[i] != pos2.m_bakStack[i]) {
		    sb.append("m_bakStack[").append(i).append("] differ: ").append(pos1.m_bakStack[i]).append(" vs ")
			    .append(pos2.m_bakStack[i]).append(System.lineSeparator());
		}
	    }
	} else {
	    for (int i = 0; i < pos1.m_bakStack.length; ++i) {
		if (pos1.m_bakStack[i] != pos2.m_bakStack[i]) {
		    sb.append("m_bakStack[").append(i).append("] differ: ").append(pos1.m_bakStack[i]).append(" vs ")
			    .append(pos2.m_bakStack[i]).append(System.lineSeparator());
		}
	    }
	}
	if (pos1.m_bakIndex != pos2.m_bakIndex) {
	    sb.append("m_bakIndex differ").append(System.lineSeparator());
	}
	// array:
	if (pos1.m_moveStack.length != pos2.m_moveStack.length) {
	    sb.append("m_moveStack lengths differ").append(System.lineSeparator());
	    for (int i = 0; i < Math.min(pos1.m_moveStack.length, pos2.m_moveStack.length); ++i) {
		if (pos1.m_moveStack[i] != pos2.m_moveStack[i]) {
		    sb.append("m_moveStack[").append(i).append("] differ: ").append(Move.getString(pos1.m_moveStack[i]))
			    .append(" vs ").append(Move.getString(pos2.m_moveStack[i])).append(System.lineSeparator());
		}
	    }
	} else {
	    for (int i = 0; i < pos1.m_moveStack.length; ++i) {
		if (pos1.m_moveStack[i] != pos2.m_moveStack[i]) {
		    sb.append("m_moveStack[").append(i).append("] differ: ").append(Move.getString(pos1.m_moveStack[i]))
			    .append(" vs ").append(Move.getString(pos2.m_moveStack[i])).append(System.lineSeparator());
		}
	    }
	}
	if (pos1.m_moveStackIndex != pos2.m_moveStackIndex) {
	    sb.append("m_moveStackIndex differ").append(System.lineSeparator());
	}
	// array:
	if (pos1.m_moves.length != pos2.m_moves.length) {
	    sb.append("m_moves lengths differ").append(System.lineSeparator());
	    for (int i = 0; i < Math.min(pos1.m_moves.length, pos2.m_moves.length); ++i) {
		if (pos1.m_moves[i] != pos2.m_moves[i]) {
		    sb.append("m_moves[").append(i).append("] differ: ").append(Move.getString(pos1.m_moves[i]))
			    .append(" vs ").append(Move.getString(pos2.m_moves[i])).append(System.lineSeparator());
		}
	    }
	} else {
	    for (int i = 0; i < pos1.m_moves.length; ++i) {
		if (pos1.m_moves[i] != pos2.m_moves[i]) {
		    sb.append("m_moves[").append(i).append("] differ: ").append(Move.getString(pos1.m_moves[i]))
			    .append(" vs ").append(Move.getString(pos2.m_moves[i])).append(System.lineSeparator());
		}
	    }
	}
	if (pos1.m_variant != pos2.m_variant) {
	    sb.append("m_variant differ").append(System.lineSeparator());
	}
	if (pos1.m_chess960KingFile != pos2.m_chess960KingFile) {
	    sb.append("m_chess960KingFile differ").append(System.lineSeparator());
	}
	if (pos1.m_chess960QueensideRookFile != pos2.m_chess960QueensideRookFile) {
	    sb.append("m_chess960QueensideRookFile differ").append(System.lineSeparator());
	}
	if (pos1.m_chess960KingsideRookFile != pos2.m_chess960KingsideRookFile) {
	    sb.append("m_chess960KingsideRookFile differ").append(System.lineSeparator());
	}
	return sb.toString();
    }

    /*
     * This class is made only for tests!
     */

    public class PosInternalState {
	private int bakIndex;
	private List<Long> bakStack = new ArrayList<>();
	private long bbWhites, bbBlacks, bbPawns, bbKnights, bbBishops, bbRooks;
	private int moveStackIndex;
	private List<Short> moveStack = new ArrayList<>();

	public PosInternalState() {
	    bakIndex = m_bakIndex;
	    for (long l : m_bakStack) {
		if (l == 0L) {
		    break;
		} else {
		    bakStack.add(l);
		}
	    }
	    bbWhites = m_bbWhites;
	    bbBlacks = m_bbBlacks;
	    bbPawns = m_bbPawns;
	    bbKnights = m_bbKnights;
	    bbBishops = m_bbBishops;
	    bbRooks = m_bbRooks;
	    moveStackIndex = m_moveStackIndex;
	    for (short s : m_moveStack) {
		if (s == 0) {
		    break;
		} else {
		    moveStack.add(s);
		}
	    }
	}

	@Override
	public boolean equals(Object other) {
	    if (other instanceof PosInternalState) {
		PosInternalState otherState = (PosInternalState) other;
		boolean retVal = true;
		if (this.bakIndex != otherState.bakIndex) {
		    System.err.println("bakindex: " + this.bakIndex + " / " + otherState.bakIndex);
		    retVal = false;
		}
		if (!this.bakStack.equals(otherState.bakStack)) {
		    System.err.println("bakStack!");
		    if (this.bakStack.size() != otherState.bakStack.size()) {
			System.err.println("   Sizes differ");
		    }
		    for (int index = 0; index < Math.min(this.bakStack.size(), otherState.bakStack.size()); ++index) {
			if (!this.bakStack.get(index).equals(otherState.bakStack.get(index))) {
			    System.err.println("   " + index + ": " + this.bakStack.get(index) + " / "
				    + otherState.bakStack.get(index));
			}
		    }
		    retVal = false;
		}
		if (this.bbWhites != otherState.bbWhites) {
		    System.err.println("Whites: " + this.bbWhites + " / " + otherState.bbWhites);
		    retVal = false;
		}
		if (this.bbBlacks != otherState.bbBlacks) {
		    System.err.println("Blacks: " + this.bbBlacks + " / " + otherState.bbBlacks);
		    retVal = false;
		}
		if (this.bbPawns != otherState.bbPawns) {
		    System.err.println("Pawns: " + this.bbPawns + " / " + otherState.bbPawns);
		    retVal = false;
		}
		if (this.bbKnights != otherState.bbKnights) {
		    System.err.println("Knights: " + this.bbKnights + " / " + otherState.bbKnights);
		    retVal = false;
		}
		if (this.bbBishops != otherState.bbBishops) {
		    System.err.println("Bishops: " + this.bbBishops + " / " + otherState.bbBishops);
		    retVal = false;
		}
		if (this.bbRooks != otherState.bbRooks) {
		    System.err.println("Rooks: " + this.bbRooks + " / " + otherState.bbRooks);
		    retVal = false;
		}
		if (this.moveStackIndex != otherState.moveStackIndex) {
		    System.err.println("moveStackIndex: " + this.moveStackIndex + " / " + otherState.moveStackIndex);
		    retVal = false;
		}
		if (!this.moveStack.equals(otherState.moveStack)) {
		    System.err.println("moveStack!");
		    if (this.moveStack.size() != otherState.moveStack.size()) {
			System.err.println("   Sizes differ");
		    }
		    for (int index = 0; index < Math.min(this.moveStack.size(), otherState.moveStack.size()); ++index) {
			if (!this.moveStack.get(index).equals(otherState.moveStack.get(index))) {
			    System.err.println("   " + index + ": " + this.moveStack.get(index) + " / "
				    + otherState.moveStack.get(index));
			}
		    }
		    retVal = false;
		}
		return retVal;
	    } else {
		return false;
	    }
	}
    }

    public PosInternalState getInternalState() {
	return new PosInternalState();
    }
}

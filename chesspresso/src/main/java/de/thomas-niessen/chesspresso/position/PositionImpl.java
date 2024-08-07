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

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import chesspresso.Chess;
import chesspresso.Variant;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;

public final class PositionImpl extends AbstractMoveablePosition implements Serializable {

	@Serial
	private static final long serialVersionUID = 2L;
	private final static boolean DEBUG = false;
	private final static boolean PROFILE = false;

	private static long numIsAttacked = 0;
	private static long numAllAttackers = 0;
	private static long numIsCheck = 0;
	private static long numIsMate = 0;
	private static long numIsStaleMate = 0;
	private static long numGetAllMoves = 0;
	private static long numPositions = 0;
	private static long numGetPinnedDirection = 0;
	private static long numDoMove = 0;
	private static long numLongsBackuped = 0;
	private static long numUndoMove = 0;
	private static long numSet = 0;
	private static long numGetSquare = 0;

	/*
	 * =========================================================================
	 */
	// Bit Board operations
	// put here for performance (inlining)
	// do before hashing!

	private final static long[] S_OF_COL, S_OF_ROW, S_OF_SQUARE;

	static {
		S_OF_COL = new long[Chess.NUM_OF_COLS];
		for (int col = 0; col < Chess.NUM_OF_COLS; col++) {
			S_OF_COL[col] = 0x0101010101010101L << col;
		}

		S_OF_ROW = new long[Chess.NUM_OF_ROWS];
		for (int row = 0; row < Chess.NUM_OF_ROWS; row++) {
			S_OF_ROW[row] = 255L << (8 * row);
		}

		S_OF_SQUARE = new long[Chess.NUM_OF_SQUARES];
		for (int sqi = 0; sqi < Chess.NUM_OF_SQUARES; sqi++) {
			S_OF_SQUARE[sqi] = 1L << sqi;
		}
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

	private static long ofSquare(int sqi) {
		return S_OF_SQUARE[sqi];
	}

	private static long ofCol(int col) {
		return S_OF_COL[col];
	}

	private static long ofRow(int row) {
		return S_OF_ROW[row];
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

	@SuppressWarnings("unused")
	private static long getFirstSqiBB(long bb) // returns 0 if no bit set, not -1!!!
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
	// see pawn move dir vs pawn capture dir

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
				}
			}
			RAY[from] = new long[NUM_OF_DIRS];
			for (int dir = 0; dir < NUM_OF_DIRS; dir++) {
				long bb = ofSquare(from);
				for (;;) {
					RAY[from][dir] |= bb;
					if ((bb & RIM_BOARD[dir]) != 0L) {
						break;
					}
					if (DIR_SHIFT[dir] < 0) {
						bb >>>= -DIR_SHIFT[dir];
					} else {
						bb <<= DIR_SHIFT[dir];
					}
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

		if (Math.abs(dcol) != Math.abs(drow) && dcol != 0 && drow != 0) {
			return NO_DIR;
		}

		dcol = sign(dcol);
		drow = sign(drow);
		if (dcol == -1 && drow == -1) {
			return SW;
		}
		if (dcol == -1 && drow == 0) {
			return W;
		}
		if (dcol == -1 && drow == 1) {
			return NW;
		}
		if (dcol == 0 && drow == -1) {
			return S;
		}
		if (dcol == 0 && drow == 1) {
			return N;
		}
		if (dcol == 1 && drow == -1) {
			return SE;
		}
		if (dcol == 1 && drow == 0) {
			return E;
		}
		if (dcol == 1 && drow == 1) {
			return NE;
		}
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
	private static final long WHITE_LONG_CASTLE_EMPTY_MASK = ofSquare(Chess.D1) | ofSquare(Chess.C1) | ofSquare(Chess.B1);
	private static final long BLACK_SHORT_CASTLE_EMPTY_MASK = ofSquare(Chess.F8) | ofSquare(Chess.G8);
	private static final long BLACK_LONG_CASTLE_EMPTY_MASK = ofSquare(Chess.D8) | ofSquare(Chess.C8) | ofSquare(Chess.B8);

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
					if (dcol == 0 && drow == 1) {
						WHITE_PAWN_MOVES[from] |= bbTo;
					}
					if (dcol == 0 && drow == -1) {
						BLACK_PAWN_MOVES[from] |= bbTo;
					}
					if (dcol == -1 && drow == 1) {
						WHITE_PAWN_ATTACKS[from] |= bbTo;
					}
					if (dcol == 1 && drow == 1) {
						WHITE_PAWN_ATTACKS[from] |= bbTo;
					}
					if (dcol == -1 && drow == -1) {
						BLACK_PAWN_ATTACKS[from] |= bbTo;
					}
					if (dcol == 1 && drow == -1) {
						BLACK_PAWN_ATTACKS[from] |= bbTo;
					}
				}
			}
			QUEEN_ATTACKS[from] = BISHOP_ATTACKS[from] | ROOK_ATTACKS[from];
			ALL_ATTACKS[from] = QUEEN_ATTACKS[from] | KNIGHT_ATTACKS[from];
		}
	}

	/*
	 * =========================================================================
	 */
	// settings for information flags in myFlags

	private final static int
	// FLAG_UNKNOWN = 0,
	FLAG_YES = 1, FLAG_NO = 2, FLAG_MASK = 0x3;
	private final static int TO_PLAY_SHIFT = 0, TO_PLAY_MASK = 0x01, CASTLES_SHIFT = 1, CASTLES_MASK = 0x0F, SQI_EP_SHIFT = 5,
			SQI_EP_MASK = 0x7F, HASH_COL_EP_SHIFT = 12, HASH_COL_EP_MASK = 0x0F, CHECK_SHIFT = 16, CHECK_MASK = FLAG_MASK,
			CAN_MOVE_SHIFT = 18, CAN_MOVE_MASK = FLAG_MASK, HALF_MOVE_CLOCK_SHIFT = 20, HALF_MOVE_CLOCK_MASK = 0xFF,
			PLY_NUMBER_SHIFT = 28, PLY_NUMBER_MASK = 0x3FF;

	private final static int OTHER_CHANGE_MOVE = Move.OTHER_SPECIALS;

	// can use up to 47 bits (64 bits - 2 * 6 to store king squares - 5 for change mask)

	/*
	 * =========================================================================
	 */

	// Exceptional use of a prefix.

	private long myBbWhites, myBbBlacks, myBbPawns, myBbKnights, myBbBishops, myBbRooks;
	private int myWhiteKing = Chess.NO_SQUARE, myBlackKing = Chess.NO_SQUARE; // actually only a short (6 bit) is needed
	private long myFlags;
	private long myHashCode;

	private long[] myBakStack;
	private int myBakIndex;
	private short[] myMoveStack;
	private int myMoveStackIndex;

	private Variant myVariant = Variant.STANDARD;
	private int myChess960CastlingFiles = 0;

	private final short[] allMoves = new short[256]; // buffer for getAllMoves,
	// allocated once for efficiency
	// TN: Is 256 large enough? It is said that
	// "R6R/3Q4/1Q4Q1/4Q3/2Q4Q/Q4Q2/pp1Q4/kBNN1KB1 w - - 0 1"
	// has the largest number of possible moves: 218! So, 256 should always work.

	// The coding is as follows: The three final bits are used as flags which indicate whether the corresponding
	// file is set or not. Then three bits are left free for a possible Double Fischer Random Chess implementation.
	// Then each file is stored in three further bits with the values Chess.A_FILE, ... , Chess.H_FIle.

	private final static int CHESS960_KING_FILE_FLAG_SHIFT = 0, CHESS960_QUEENSIDE_ROOK_FILE_FLAG_SHIFT = 1,
			CHESS960_KINGSIDE_ROOK_FILE_FLAG_SHIFT = 2, CHESS960_KING_FILE_SHIFT = 6, CHESS960_QUEENSIDE_ROOK_FILE_SHIFT = 9,
			CHESS960_KINGSIDE_ROOK_FILE_SHIFT = 12;

	private final static int CHESS960_KING_FILE_FLAG_MASK = 1 << CHESS960_KING_FILE_FLAG_SHIFT,
			CHESS960_QUEENSIDE_ROOK_FILE_FLAG_MASK = 1 << CHESS960_QUEENSIDE_ROOK_FILE_FLAG_SHIFT,
			CHESS960_KINGSIDE_ROOK_FILE_FLAG_MASK = 1 << CHESS960_KINGSIDE_ROOK_FILE_FLAG_SHIFT,
			CHESS960_KING_FILE_MASK = 7 << CHESS960_KING_FILE_SHIFT,
			CHESS960_QUEENSIDE_ROOK_FILE_MASK = 7 << CHESS960_QUEENSIDE_ROOK_FILE_SHIFT,
			CHESS960_KINGSIDE_ROOK_FILE_MASK = 7 << CHESS960_KINGSIDE_ROOK_FILE_SHIFT;

	/*
	 * =========================================================================
	 */

	@Override
	public int getWhitesKingSquare() {
		return myWhiteKing;
	}

	@Override
	public int getBlacksKingSquare() {
		return myBlackKing;
	}

	@Override
	public long getAllPawnsBB() {
		return myBbPawns;
	}

	@Override
	public long getWhitePawnsBB() {
		return myBbPawns & myBbWhites;
	}

	@Override
	public long getBlackPawnsBB() {
		return myBbPawns & myBbBlacks;
	}

	/*
	 * =========================================================================
	 */

	PositionImpl() {
		this(60); // make room for 120 plies
	}

	PositionImpl(int bufferLength) {
		if (PROFILE) {
			numPositions++;
		}
		myBakStack = new long[4 * bufferLength]; // on average, we need about 3.75 longs to back up a position
		myMoveStack = new short[bufferLength];
		clear();
	}

	PositionImpl(ImmutablePosition pos) {
		this();
		setPositionSnapshot(pos);
	}

	PositionImpl(String fen, boolean strict) throws InvalidFenException {
		this();
		FEN.initFromFEN(this, fen, strict);
	}

	/*
	 * =========================================================================
	 */

	@Override
	public void setPositionSnapshot(ImmutablePosition position) {
		clear(); // Needed. Because there is no direct way to reset the ply number.
		super.setPositionSnapshot(position);
		this.myVariant = position.getVariant();
		if (myVariant == Variant.CHESS960) {
			setChess960CastlingFiles(position.getChess960KingFile(), position.getChess960QueensideRookFile(),
					position.getChess960KingsideRookFile());
		}
		this.plyOffset = position.getPlyNumber();
	}

	/*
	 * =========================================================================
	 */

	@Override
	public void clear() {
		super.clear();
		myFlags = 0L;
	}

	private void clearStacks() {
		int index = 0;
		while (index < myBakStack.length && myBakStack[index] != 0L) {
			myBakStack[index] = 0L;
			++index;
		}
		myBakIndex = 0;
		index = 0;
		while (index < myMoveStack.length && myMoveStack[index] != 0) {
			myMoveStack[index] = 0;
			++index;
		}
		myMoveStackIndex = 0;
		index = 0;
		while (index < allMoves.length && allMoves[index] != 0) {
			allMoves[index] = 0;
			++index;
		}
	}

	/*
	 * =========================================================================
	 */

	@Override
	public void initFromFEN(String fen, boolean validate) throws InvalidFenException {
		clear();
		clearStacks();
		FEN.initFromFEN(this, fen, validate);
	}

	/*
	 * =========================================================================
	 */

	@Override
	public int getToPlay() {
		return ((myFlags >>> TO_PLAY_SHIFT) & TO_PLAY_MASK) == 0 ? Chess.WHITE : Chess.BLACK;
	}

	private int getNotToPlay() {
		return ((myFlags >>> TO_PLAY_SHIFT) & TO_PLAY_MASK) != 0 ? Chess.WHITE : Chess.BLACK;
	}

	@Override
	public boolean isSquareEmpty(int sqi) {
		return ((myBbWhites | myBbBlacks) & ofSquare(sqi)) == 0L;
	}

	@Override
	public int getCastles() {
		return (int) (myFlags >>> CASTLES_SHIFT) & CASTLES_MASK;
	}

	@Override
	public int getSqiEP() {
		return (int) ((myFlags >>> SQI_EP_SHIFT) & SQI_EP_MASK) + Chess.NO_SQUARE;
	}

	private int getHashColEP() {
		return (int) ((myFlags >>> HASH_COL_EP_SHIFT) & HASH_COL_EP_MASK) + Chess.NO_SQUARE;
	}

	@Override
	public int getHalfMoveClock() {
		return (int) (myFlags >>> HALF_MOVE_CLOCK_SHIFT) & HALF_MOVE_CLOCK_MASK;
	}

	@Override
	public int getPlyNumber() {
		int plies = (int) ((myFlags >>> PLY_NUMBER_SHIFT) & PLY_NUMBER_MASK);
		return plyOffset + plies;
	}

	@Override
	public long getHashCode() {
		return myHashCode;
	}

	@Override
	public int getStone(int sqi) {
		if (PROFILE) {
			numGetSquare++;
		}

		long bbSqi = ofSquare(sqi);
		if ((myBbWhites & bbSqi) != 0L) {
			if ((myBbPawns & bbSqi) != 0L) {
				return Chess.WHITE_PAWN;
			}
			if ((myBbBishops & bbSqi) != 0L) {
				return ((myBbRooks & bbSqi) != 0L ? Chess.WHITE_QUEEN : Chess.WHITE_BISHOP);
			}
			if ((myBbKnights & bbSqi) != 0L) {
				return Chess.WHITE_KNIGHT;
			}
			if ((myBbRooks & bbSqi) != 0L) {
				return Chess.WHITE_ROOK;
			}
			return Chess.WHITE_KING;
		} else if ((myBbBlacks & bbSqi) != 0L) {
			if ((myBbPawns & bbSqi) != 0L) {
				return Chess.BLACK_PAWN;
			}
			if ((myBbBishops & bbSqi) != 0L) {
				return ((myBbRooks & bbSqi) != 0L ? Chess.BLACK_QUEEN : Chess.BLACK_BISHOP);
			}
			if ((myBbKnights & bbSqi) != 0L) {
				return Chess.BLACK_KNIGHT;
			}
			if ((myBbRooks & bbSqi) != 0L) {
				return Chess.BLACK_ROOK;
			}
			return Chess.BLACK_KING;
		} else {
			return Chess.NO_STONE;
		}
	}

	@Override
	public int getPiece(int sqi) {
		if (PROFILE) {
			numGetSquare++;
		}

		long bbSqi = ofSquare(sqi);
		if ((myBbPawns & bbSqi) != 0L) {
			return Chess.PAWN;
		}
		if ((myBbKnights & bbSqi) != 0L) {
			return Chess.KNIGHT;
		}
		if ((myBbBishops & bbSqi) != 0L) {
			return ((myBbRooks & bbSqi) != 0L ? Chess.QUEEN : Chess.BISHOP);
		}
		if ((myBbRooks & bbSqi) != 0L) {
			return Chess.ROOK;
		}
		if (((myBbWhites & bbSqi) != 0L && sqi == myWhiteKing) || ((myBbBlacks & bbSqi) != 0L && sqi == myBlackKing)) {
			return Chess.KING;
		}
		return Chess.NO_PIECE;
	}

	@Override
	public int getColor(int sqi) {
		if (PROFILE) {
			numGetSquare++;
		}

		long bbSqi = ofSquare(sqi);
		if ((myBbWhites & bbSqi) != 0L) {
			return Chess.WHITE;
		}
		if ((myBbBlacks & bbSqi) != 0L) {
			return Chess.BLACK;
		}
		return Chess.NOBODY;
	}

	private long getBitBoard(int stone) {
		return switch (stone) {
		case Chess.NO_STONE -> 0L;
		case Chess.WHITE_KING -> ofSquare(myWhiteKing);
		case Chess.WHITE_PAWN -> myBbPawns & myBbWhites;
		case Chess.WHITE_KNIGHT -> myBbKnights & myBbWhites;
		case Chess.WHITE_BISHOP -> myBbBishops & (~myBbRooks) & myBbWhites;
		case Chess.WHITE_ROOK -> myBbRooks & (~myBbBishops) & myBbWhites;
		case Chess.WHITE_QUEEN -> myBbBishops & myBbRooks & myBbWhites;
		case Chess.BLACK_KING -> ofSquare(myBlackKing);
		case Chess.BLACK_PAWN -> myBbPawns & myBbBlacks;
		case Chess.BLACK_KNIGHT -> myBbKnights & myBbBlacks;
		case Chess.BLACK_BISHOP -> myBbBishops & (~myBbRooks) & myBbBlacks;
		case Chess.BLACK_ROOK -> myBbRooks & (~myBbBishops) & myBbBlacks;
		case Chess.BLACK_QUEEN -> myBbBishops & myBbRooks & myBbBlacks;
		default -> throw new IllegalArgumentException("Unknown stone in PpsitionImpl::getBitBoard: " + stone);
		};
	}

	/*
	 * =========================================================================
	 */

	@Override
	public void setStone(int sqi, int stone) {
		setStone(sqi, stone, true);
	}

	private void setStone(int sqi, int stone, boolean clearStacksAndFlags) {
		if (PROFILE) {
			numSet++;
		}

		if (DEBUG) {
			System.out.println("Set " + Chess.stoneToChar(stone) + " to " + Chess.sqiToStr(sqi));
		}

		/*---------- remove an old king on another square ----------*/
		if (stone == Chess.WHITE_KING && myWhiteKing != Chess.NO_SQUARE && myWhiteKing != sqi) {
			setStone(myWhiteKing, Chess.NO_STONE);
		} else if (stone == Chess.BLACK_KING && myBlackKing != Chess.NO_SQUARE && myBlackKing != sqi) {
			setStone(myBlackKing, Chess.NO_STONE);
		}

		int old = getStone(sqi);
		if (old != stone) {
			long bbSqi = ofSquare(sqi);

			/*---------- remove stone from sqi ----------*/
			switch (old) {
			case Chess.NO_STONE:
				break;
			case Chess.WHITE_KING:
				myBbWhites &= ~bbSqi;
				myWhiteKing = Chess.NO_SQUARE;
				break;
			case Chess.WHITE_PAWN:
				myBbWhites &= ~bbSqi;
				myBbPawns &= ~bbSqi;
				break;
			case Chess.WHITE_KNIGHT:
				myBbWhites &= ~bbSqi;
				myBbKnights &= ~bbSqi;
				break;
			case Chess.WHITE_BISHOP:
				myBbWhites &= ~bbSqi;
				myBbBishops &= ~bbSqi;
				break;
			case Chess.WHITE_ROOK:
				myBbWhites &= ~bbSqi;
				myBbRooks &= ~bbSqi;
				break;
			case Chess.WHITE_QUEEN:
				myBbWhites &= ~bbSqi;
				myBbBishops &= ~bbSqi;
				myBbRooks &= ~bbSqi;
				break;
			case Chess.BLACK_KING:
				myBbBlacks &= ~bbSqi;
				myBlackKing = Chess.NO_SQUARE;
				break;
			case Chess.BLACK_PAWN:
				myBbBlacks &= ~bbSqi;
				myBbPawns &= ~bbSqi;
				break;
			case Chess.BLACK_KNIGHT:
				myBbBlacks &= ~bbSqi;
				myBbKnights &= ~bbSqi;
				break;
			case Chess.BLACK_BISHOP:
				myBbBlacks &= ~bbSqi;
				myBbBishops &= ~bbSqi;
				break;
			case Chess.BLACK_ROOK:
				myBbBlacks &= ~bbSqi;
				myBbRooks &= ~bbSqi;
				break;
			case Chess.BLACK_QUEEN:
				myBbBlacks &= ~bbSqi;
				myBbBishops &= ~bbSqi;
				myBbRooks &= ~bbSqi;
				break;
			}

			/*---------- add new stone to sqi ----------*/
			switch (stone) {
			case Chess.NO_STONE:
				break;
			case Chess.WHITE_KING:
				myBbWhites |= bbSqi;
				myWhiteKing = sqi;
				break;
			case Chess.WHITE_PAWN:
				myBbWhites |= bbSqi;
				myBbPawns |= bbSqi;
				break;
			case Chess.WHITE_KNIGHT:
				myBbWhites |= bbSqi;
				myBbKnights |= bbSqi;
				break;
			case Chess.WHITE_BISHOP:
				myBbWhites |= bbSqi;
				myBbBishops |= bbSqi;
				break;
			case Chess.WHITE_ROOK:
				myBbWhites |= bbSqi;
				myBbRooks |= bbSqi;
				break;
			case Chess.WHITE_QUEEN:
				myBbWhites |= bbSqi;
				myBbBishops |= bbSqi;
				myBbRooks |= bbSqi;
				break;
			case Chess.BLACK_KING:
				myBbBlacks |= bbSqi;
				myBlackKing = sqi;
				break;
			case Chess.BLACK_PAWN:
				myBbBlacks |= bbSqi;
				myBbPawns |= bbSqi;
				break;
			case Chess.BLACK_KNIGHT:
				myBbBlacks |= bbSqi;
				myBbKnights |= bbSqi;
				break;
			case Chess.BLACK_BISHOP:
				myBbBlacks |= bbSqi;
				myBbBishops |= bbSqi;
				break;
			case Chess.BLACK_ROOK:
				myBbBlacks |= bbSqi;
				myBbRooks |= bbSqi;
				break;
			case Chess.BLACK_QUEEN:
				myBbBlacks |= bbSqi;
				myBbBishops |= bbSqi;
				myBbRooks |= bbSqi;
				break;
			}

			/*---------- hash value ----------*/
			if (old != Chess.NO_STONE) {
				myHashCode ^= HASH_MOD[sqi][old - Chess.MIN_STONE];
			}
			if (stone != Chess.NO_STONE) {
				myHashCode ^= HASH_MOD[sqi][stone - Chess.MIN_STONE];
			}

			if (clearStacksAndFlags) {
				clearStacks();
				/*---------- delete position properties in myFlags ----------*/
				// TN: This is taken from doMove. myFlags = 0l; is not the right idea, because tests showed that
				// for some reason the value of the hash code will later be changed!
				myFlags &= ~(CHECK_MASK << CHECK_SHIFT); // delete isCheck info
				myFlags &= ~(CAN_MOVE_MASK << CAN_MOVE_SHIFT); // delete canMove info
				// This is needed additionally:
				myFlags &= ~((long) PLY_NUMBER_MASK << PLY_NUMBER_SHIFT);

				// TN: What about the half move clock? The following line looks reasonable
				// myFlags &= ~(HALF_MOVE_CLOCK_MASK << HALF_MOVE_CLOCK_SHIFT);
				// but then the tests fail!
				// TN: And what about castles? With the following line
				// myFlags &= ~(CASTLES_MASK << CASTLES_SHIFT);
				// at least AbstractMutablePosition::invert fails!
			}
		}
	}

	private void incPlyNumber() {
		// By the bit operations a ply number will always be between 0 and 1023!
		if (DEBUG) {
			System.out.println("incPlyNumber");
		}
		myFlags += 1L << PLY_NUMBER_SHIFT;
	}

	@Override
	public void setHalfMoveClock(int halfMoveClock) {
		// By the bit operations a half move clock number will always be between 0 and 255!
		if (DEBUG) {
			System.out.println("setHalfMoveClock " + halfMoveClock);
		}
		myFlags &= ~(HALF_MOVE_CLOCK_MASK << HALF_MOVE_CLOCK_SHIFT);
		myFlags |= (long) halfMoveClock << HALF_MOVE_CLOCK_SHIFT;
	}

	@Override
	public void setCastles(int castles) {
		if (DEBUG) {
			System.out.println("setCastles " + castles);
		}
		int oldCastles = getCastles();
		if (oldCastles != castles) {
			myFlags &= ~(CASTLES_MASK << CASTLES_SHIFT);
			myFlags |= (long) castles << CASTLES_SHIFT;
			/*---------- hash value ----------*/
			myHashCode ^= HASH_CASTLE_MOD[oldCastles];
			myHashCode ^= HASH_CASTLE_MOD[castles];
		}
	}

	@Override
	public void setSqiEP(int sqiEP) {
		if (DEBUG) {
			System.out.println("setSqiEP " + sqiEP);
		}
		if (getSqiEP() != sqiEP) {
			myFlags &= ~(SQI_EP_MASK << SQI_EP_SHIFT);
			myFlags |= (long) (sqiEP - Chess.NO_SQUARE) << SQI_EP_SHIFT;

			/*---------- hash value ----------*/
			int hashColEP = getHashColEP();
			if (hashColEP != Chess.NO_SQUARE) {
				myHashCode ^= HASH_EP_MOD[hashColEP];
			}

			hashColEP = (sqiEP == Chess.NO_COL ? Chess.NO_COL : Chess.sqiToCol(sqiEP));
			// ignore ep square for hashing if there is no opponent pawn to actually capture the pawn ep
			// only in this case is the position different

			if (sqiEP != Chess.NO_COL) {
				if (sqiEP < Chess.A4) { // test is independent of whether side is to play
					// is set before or afterwards
					if ((WHITE_PAWN_ATTACKS[sqiEP] & myBbPawns & myBbBlacks) == 0L) {
						hashColEP = Chess.NO_COL;
					}
				} else {
					if ((BLACK_PAWN_ATTACKS[sqiEP] & myBbPawns & myBbWhites) == 0L) {
						hashColEP = Chess.NO_COL;
					}
				}
				if (hashColEP != Chess.NO_COL) {
					myHashCode ^= HASH_EP_MOD[hashColEP];
				}
			}
			myFlags &= ~(HASH_COL_EP_MASK << HASH_COL_EP_SHIFT);
			// encode column of ep square in hash code (NO_SQUARE if no ep)
			myFlags |= (long) (hashColEP - Chess.NO_SQUARE) << HASH_COL_EP_SHIFT;
		}
	}

	@Override
	public void setToPlay(int toPlay) {
		if (DEBUG) {
			System.out.println("setToPlay " + toPlay);
		}
		if (toPlay != getToPlay()) {
			toggleToPlay();
		}
	}

	@Override
	public void toggleToPlay() {
		if (DEBUG) {
			System.out.println("toggleToPlay");
		}
		myFlags ^= (TO_PLAY_MASK << TO_PLAY_SHIFT);
		/*---------- hash value ----------*/
		myHashCode ^= HASH_TOPLAY_MULT;
	}

	private void setMove(short move) throws IllegalMoveException {
		if (DEBUG) {
			System.out.println(getPlyNumber() + ": " + Move.getString(move));
		}

		boolean increaseHalfMoveClock = true;
		int sqiEP = Chess.NO_SQUARE;
		//	long squaresChanged = 0L;

		if (!Move.isNullMove(move)) {
			/*---------- moves the pieces ----------*/
			if (Move.isCastle(move)) {
				if (getToPlay() == Chess.WHITE) {
					if (Move.isShortCastle(move)) {
						//			squaresChanged = WHITE_SHORT_CASTLE_KING_CHANGE_MASK | WHITE_SHORT_CASTLE_ROOK_CHANGE_MASK;
						myBbWhites ^= WHITE_SHORT_CASTLE_KING_CHANGE_MASK | WHITE_SHORT_CASTLE_ROOK_CHANGE_MASK;
						myWhiteKing = Chess.G1;
						myBbRooks ^= WHITE_SHORT_CASTLE_ROOK_CHANGE_MASK;
						myHashCode ^= HASH_MOD[Chess.E1][Chess.WHITE_KING - Chess.MIN_STONE];
						myHashCode ^= HASH_MOD[Chess.F1][Chess.WHITE_ROOK - Chess.MIN_STONE];
						myHashCode ^= HASH_MOD[Chess.G1][Chess.WHITE_KING - Chess.MIN_STONE];
						myHashCode ^= HASH_MOD[Chess.H1][Chess.WHITE_ROOK - Chess.MIN_STONE];
					} else {
						//			squaresChanged = WHITE_LONG_CASTLE_KING_CHANGE_MASK | WHITE_LONG_CASTLE_ROOK_CHANGE_MASK;
						myBbWhites ^= WHITE_LONG_CASTLE_KING_CHANGE_MASK | WHITE_LONG_CASTLE_ROOK_CHANGE_MASK;
						myWhiteKing = Chess.C1;
						myBbRooks ^= WHITE_LONG_CASTLE_ROOK_CHANGE_MASK;
						myHashCode ^= HASH_MOD[Chess.E1][Chess.WHITE_KING - Chess.MIN_STONE];
						myHashCode ^= HASH_MOD[Chess.D1][Chess.WHITE_ROOK - Chess.MIN_STONE];
						myHashCode ^= HASH_MOD[Chess.C1][Chess.WHITE_KING - Chess.MIN_STONE];
						myHashCode ^= HASH_MOD[Chess.A1][Chess.WHITE_ROOK - Chess.MIN_STONE];
					}
					excludeCastles(WHITE_CASTLE);
				} else {
					if (Move.isShortCastle(move)) {
						//			squaresChanged = BLACK_SHORT_CASTLE_KING_CHANGE_MASK | BLACK_SHORT_CASTLE_ROOK_CHANGE_MASK;
						myBbBlacks ^= BLACK_SHORT_CASTLE_KING_CHANGE_MASK | BLACK_SHORT_CASTLE_ROOK_CHANGE_MASK;
						myBlackKing = Chess.G8;
						myBbRooks ^= BLACK_SHORT_CASTLE_ROOK_CHANGE_MASK;
						myHashCode ^= HASH_MOD[Chess.E8][Chess.BLACK_KING - Chess.MIN_STONE];
						myHashCode ^= HASH_MOD[Chess.F8][Chess.BLACK_ROOK - Chess.MIN_STONE];
						myHashCode ^= HASH_MOD[Chess.G8][Chess.BLACK_KING - Chess.MIN_STONE];
						myHashCode ^= HASH_MOD[Chess.H8][Chess.BLACK_ROOK - Chess.MIN_STONE];
					} else {
						//			squaresChanged = BLACK_LONG_CASTLE_KING_CHANGE_MASK | BLACK_LONG_CASTLE_ROOK_CHANGE_MASK;
						myBbBlacks ^= BLACK_LONG_CASTLE_KING_CHANGE_MASK | BLACK_LONG_CASTLE_ROOK_CHANGE_MASK;
						myBlackKing = Chess.C8;
						myBbRooks ^= BLACK_LONG_CASTLE_ROOK_CHANGE_MASK;
						myHashCode ^= HASH_MOD[Chess.E8][Chess.BLACK_KING - Chess.MIN_STONE];
						myHashCode ^= HASH_MOD[Chess.D8][Chess.BLACK_ROOK - Chess.MIN_STONE];
						myHashCode ^= HASH_MOD[Chess.C8][Chess.BLACK_KING - Chess.MIN_STONE];
						myHashCode ^= HASH_MOD[Chess.A8][Chess.BLACK_ROOK - Chess.MIN_STONE];
					}
					excludeCastles(BLACK_CASTLE);
				}
			} else if (Move.isCastleChess960(move)) {
				// the implementation with setStone is less efficient, but much easier
				int kingSquare = Move.getFromSqi(move);
				int rookSquare = Move.getToSqi(move);
				if (getToPlay() == Chess.WHITE) {
					if (kingSquare < rookSquare) { // a white short castle
						setStone(kingSquare, Chess.NO_STONE, false);
						setStone(rookSquare, Chess.NO_STONE, false);
						setStone(Chess.G1, Chess.WHITE_KING, false);
						setStone(Chess.F1, Chess.WHITE_ROOK, false);
					} else { // a white long castle
						setStone(kingSquare, Chess.NO_STONE, false);
						setStone(rookSquare, Chess.NO_STONE, false);
						setStone(Chess.C1, Chess.WHITE_KING, false);
						setStone(Chess.D1, Chess.WHITE_ROOK, false);
					}
					excludeCastles(WHITE_CASTLE);
				} else { // Black's castling
					if (kingSquare < rookSquare) { // a black short castle
						setStone(kingSquare, Chess.NO_STONE, false);
						setStone(rookSquare, Chess.NO_STONE, false);
						setStone(Chess.G8, Chess.BLACK_KING, false);
						setStone(Chess.F8, Chess.BLACK_ROOK, false);
					} else { // a black long castle
						setStone(kingSquare, Chess.NO_STONE, false);
						setStone(rookSquare, Chess.NO_STONE, false);
						setStone(Chess.C8, Chess.BLACK_KING, false);
						setStone(Chess.D8, Chess.BLACK_ROOK, false);
					}
					excludeCastles(BLACK_CASTLE);
				}
			} else {
				int sqiFrom = Move.getFromSqi(move);
				int sqiTo = Move.getToSqi(move);

				long bbFrom = ofSquare(sqiFrom);
				long bbTo = ofSquare(sqiTo);
				long bbFromTo = bbFrom | bbTo;
				//		squaresChanged |= bbFromTo;

				if (Move.isCapturing(move)) {
					if (DEBUG) {
						if (isSquareEmpty(sqiTo) && !(getSqiEP() == sqiTo)) {
							throw new IllegalMoveException(
									"Move " + ((getPlyNumber() + 1) / 2 + 1) + ": capture square is empty ("
											+ Integer.toBinaryString(move) + ", " + Move.getString(move) + ")");
						}
					}
					if (DEBUG) {
						if (getColor(sqiTo) == getToPlay()) {
							throw new IllegalMoveException(
									"Move " + ((getPlyNumber() + 1) / 2 + 1) + ": cannot capture own piece ("
											+ Integer.toBinaryString(move) + ", " + Move.getString(move) + ")");
						}
					}
					if (DEBUG) {
						if (getPiece(sqiTo) == Chess.KING) {
							throw new IllegalMoveException(
									"Move " + ((getPlyNumber() + 1) / 2 + 1) + ": cannot capture the king ("
											+ Integer.toBinaryString(move) + ", " + Move.getString(move) + ")");
						}
					}

					long notBBTo;
					if (Move.isEPMove(move)) {
						int pawnSqi = getSqiEP() + (getToPlay() == Chess.WHITE ? -Chess.NUM_OF_COLS : Chess.NUM_OF_COLS);
						notBBTo = ~ofSquare(pawnSqi);
						//			squaresChanged |= ~notBBTo;
						myHashCode ^= HASH_MOD[pawnSqi][(getToPlay() == Chess.WHITE ? Chess.BLACK_PAWN : Chess.WHITE_PAWN)
								- Chess.MIN_STONE];
					} else {
						notBBTo = ~bbTo;
						int capturedStone = getStone(Move.getToSqi(move));
						myHashCode ^= HASH_MOD[sqiTo][capturedStone - Chess.MIN_STONE];
					}
					myBbWhites &= notBBTo;
					myBbBlacks &= notBBTo;
					myBbPawns &= notBBTo;
					myBbKnights &= notBBTo;
					myBbBishops &= notBBTo;
					myBbRooks &= notBBTo;
					increaseHalfMoveClock = false;
				}
				if (Move.isPromotion(move)) {
					int promotionStone = Chess.pieceToStone(Move.getPromotionPiece(move), getToPlay());
					if (getToPlay() == Chess.WHITE) {
						myBbWhites ^= bbFromTo;
						myBbPawns ^= bbFrom;
						myHashCode ^= HASH_MOD[sqiFrom][Chess.WHITE_PAWN - Chess.MIN_STONE];
						switch (promotionStone) {
						case Chess.WHITE_KNIGHT -> myBbKnights ^= bbTo;
						case Chess.WHITE_BISHOP -> myBbBishops ^= bbTo;
						case Chess.WHITE_ROOK -> myBbRooks ^= bbTo;
						case Chess.WHITE_QUEEN -> {
							myBbBishops ^= bbTo;
							myBbRooks ^= bbTo;
						}
						default -> throw new IllegalMoveException(
								"Move " + ((getPlyNumber() + 1) / 2 + 1) + ": illegal promotion stone (" + promotionStone + ", "
										+ Chess.stoneToChar(promotionStone) + ")");
						}
					} else {
						myBbBlacks ^= bbFromTo;
						myBbPawns ^= bbFrom;
						myHashCode ^= HASH_MOD[sqiFrom][Chess.BLACK_PAWN - Chess.MIN_STONE];
						switch (promotionStone) {
						case Chess.BLACK_KNIGHT -> myBbKnights ^= bbTo;
						case Chess.BLACK_BISHOP -> myBbBishops ^= bbTo;
						case Chess.BLACK_ROOK -> myBbRooks ^= bbTo;
						case Chess.BLACK_QUEEN -> {
							myBbBishops ^= bbTo;
							myBbRooks ^= bbTo;
						}
						default -> {
							System.out.println("PositionImpl::setMove: IllegalMoveException at " + this);
							String message = "Move " + ((getPlyNumber() + 1) / 2 + 1) + ": illegal promotion stone ("
									+ promotionStone + ", " + Chess.stoneToChar(promotionStone) + ")";
							System.out.println(message);
							throw new IllegalMoveException(message);
						}
						}
					}
					myHashCode ^= HASH_MOD[sqiTo][promotionStone - Chess.MIN_STONE];
					increaseHalfMoveClock = false;
				} else {
					int stone = getStone(Move.getFromSqi(move));
					switch (stone) {
					case Chess.NO_STONE -> {
						System.out.println("PositionImpl::setMove: IllegalMoveException at " + this);
						String message = "Move " + ((getPlyNumber() + 1) / 2 + 1) + "(" + Move.getString(move)
								+ "): moving stone is non-existent";
						System.out.println(message);
						throw new IllegalMoveException(message);
					}
					case Chess.WHITE_KING -> {
						myBbWhites ^= bbFromTo;
						myWhiteKing = sqiTo;
					}
					case Chess.WHITE_PAWN -> {
						myBbWhites ^= bbFromTo;
						myBbPawns ^= bbFromTo;
						increaseHalfMoveClock = false;
						if (sqiTo - sqiFrom == 2 * Chess.NUM_OF_COLS) {
							sqiEP = sqiTo - Chess.NUM_OF_COLS;
						}
					}
					case Chess.WHITE_KNIGHT -> {
						myBbWhites ^= bbFromTo;
						myBbKnights ^= bbFromTo;
					}
					case Chess.WHITE_BISHOP -> {
						myBbWhites ^= bbFromTo;
						myBbBishops ^= bbFromTo;
					}
					case Chess.WHITE_ROOK -> {
						myBbWhites ^= bbFromTo;
						myBbRooks ^= bbFromTo;
					}
					case Chess.WHITE_QUEEN -> {
						myBbWhites ^= bbFromTo;
						myBbBishops ^= bbFromTo;
						myBbRooks ^= bbFromTo;
					}
					case Chess.BLACK_KING -> {
						myBbBlacks ^= bbFromTo;
						myBlackKing = sqiTo;
					}
					case Chess.BLACK_PAWN -> {
						myBbBlacks ^= bbFromTo;
						myBbPawns ^= bbFromTo;
						increaseHalfMoveClock = false;
						if (sqiFrom - sqiTo == 2 * Chess.NUM_OF_COLS) {
							sqiEP = sqiTo + Chess.NUM_OF_COLS;
						}
					}
					case Chess.BLACK_KNIGHT -> {
						myBbBlacks ^= bbFromTo;
						myBbKnights ^= bbFromTo;
					}
					case Chess.BLACK_BISHOP -> {
						myBbBlacks ^= bbFromTo;
						myBbBishops ^= bbFromTo;
					}
					case Chess.BLACK_ROOK -> {
						myBbBlacks ^= bbFromTo;
						myBbRooks ^= bbFromTo;
					}
					case Chess.BLACK_QUEEN -> {
						myBbBlacks ^= bbFromTo;
						myBbBishops ^= bbFromTo;
						myBbRooks ^= bbFromTo;
					}
					}
					myHashCode ^= HASH_MOD[sqiFrom][stone - Chess.MIN_STONE];
					myHashCode ^= HASH_MOD[sqiTo][stone - Chess.MIN_STONE];
				}

				/*---------- update castles ----------*/
				int castles = getCastles();
				if (castles != NO_CASTLES) {
					if (myVariant == Variant.STANDARD) {
						if (sqiFrom == Chess.A1) {
							castles &= ~WHITE_LONG_CASTLE;
						} else if (sqiFrom == Chess.H1) {
							castles &= ~WHITE_SHORT_CASTLE;
						} else if (sqiFrom == Chess.A8) {
							castles &= ~BLACK_LONG_CASTLE;
						} else if (sqiFrom == Chess.H8) {
							castles &= ~BLACK_SHORT_CASTLE;
						} else if (sqiFrom == Chess.E1) {
							castles &= ~WHITE_CASTLE;
						} else if (sqiFrom == Chess.E8) {
							castles &= ~BLACK_CASTLE;
						}
						if (sqiTo == Chess.A1) {
							castles &= ~WHITE_LONG_CASTLE;
						} else if (sqiTo == Chess.H1) {
							castles &= ~WHITE_SHORT_CASTLE;
						} else if (sqiTo == Chess.A8) {
							castles &= ~BLACK_LONG_CASTLE;
						} else if (sqiTo == Chess.H8) {
							castles &= ~BLACK_SHORT_CASTLE;
						}
					} else { // Chess960
						if (sqiFrom == getChess960QueensideRookFile()) {
							castles &= ~WHITE_LONG_CASTLE;
						} else if (sqiFrom == getChess960KingsideRookFile()) {
							castles &= ~WHITE_SHORT_CASTLE;
						} else if (sqiFrom == getChess960QueensideRookFile() + Chess.A8) {
							castles &= ~BLACK_LONG_CASTLE;
						} else if (sqiFrom == getChess960KingsideRookFile() + Chess.A8) {
							castles &= ~BLACK_SHORT_CASTLE;
						} else if (sqiFrom == getChess960KingFile()) {
							castles &= ~WHITE_CASTLE;
						} else if (sqiFrom == getChess960KingFile() + Chess.A8) {
							castles &= ~BLACK_CASTLE;
						}
						if (sqiTo == getChess960QueensideRookFile()) {
							castles &= ~WHITE_LONG_CASTLE;
						} else if (sqiTo == getChess960KingsideRookFile()) {
							castles &= ~WHITE_SHORT_CASTLE;
						} else if (sqiTo == getChess960QueensideRookFile() + Chess.A8) {
							castles &= ~BLACK_LONG_CASTLE;
						} else if (sqiTo == getChess960KingsideRookFile() + Chess.A8) {
							castles &= ~BLACK_SHORT_CASTLE;
						}
					}
					setCastles(castles);
				}
			}
		}

		/*---------- update to-play, ply number ----------*/
		incPlyNumber();
		toggleToPlay();

		/*---------- update ep square ----------*/
		setSqiEP(sqiEP);

		/*---------- update half move clock ----------*/
		if (increaseHalfMoveClock) {
			incHalfMoveClock();
		} else {
			resetHalfMoveClock();
		}

		/*---------- store move in stack ----------*/
		int index = myMoveStackIndex;
		checkMoveStack();
		myMoveStack[index] = move;
		myMoveStackIndex++;
	}

	private void checkMoveStack() {
		if (myMoveStackIndex >= myMoveStack.length) {
			short[] newMoveStack = new short[myMoveStack.length * 2];
			System.arraycopy(myMoveStack, 0, newMoveStack, 0, myMoveStack.length);
			myMoveStack = newMoveStack;
		}
	}

	private void checkBackupStack() {
		if (myBakIndex + 7 >= myBakStack.length) {
			long[] oldBak = myBakStack;
			myBakStack = new long[2 * oldBak.length];
			System.arraycopy(oldBak, 0, myBakStack, 0, oldBak.length);
		}
	}

	private long getAllFlags(int changeMask) {
		long allFlags = (((myFlags << 6) | myWhiteKing) << 6) | myBlackKing;
		return (allFlags << 5) | changeMask;
	}

	@SuppressWarnings("unused")
	private void takeBaseline() {
		checkBackupStack();

		myBakStack[myBakIndex++] = myHashCode;
		myBakStack[myBakIndex++] = myBbWhites;
		myBakStack[myBakIndex++] = myBbPawns;
		myBakStack[myBakIndex++] = myBbKnights;
		myBakStack[myBakIndex++] = myBbBishops;
		myBakStack[myBakIndex++] = myBbRooks;

		int changeMask = 0x1F;
		long bakFlags = (((myFlags << 6) | myWhiteKing) << 6) | myBlackKing;
		myBakStack[myBakIndex++] = (bakFlags << 5) | changeMask;
		myBakStack[myBakIndex] = 0L; // prevent redos

		checkMoveStack();
		myMoveStack[myMoveStackIndex++] = OTHER_CHANGE_MOVE;
	}

	@Override
	public void doMove(short move) throws IllegalMoveException {
		if (PROFILE) {
			numDoMove++;
		}

		if (!Move.isValid(move)) {
			throw new IllegalMoveException(move);
		}

		/*---------- back current state up ----------*/
		checkBackupStack();

		/*---------- take baseline ----------*/
		long bakWhites = myBbWhites;
		long bakPawns = myBbPawns;
		long bakKnights = myBbKnights;
		long bakBishops = myBbBishops;
		long bakRooks = myBbRooks;
		long bakFlags = (((myFlags << 6) | myWhiteKing) << 6) | myBlackKing;

		myBakStack[myBakIndex++] = myHashCode;

		/*---------- delete position properties in myFlags ----------*/
		myFlags &= ~(CHECK_MASK << CHECK_SHIFT); // delete isCheck info
		myFlags &= ~(CAN_MOVE_MASK << CAN_MOVE_SHIFT); // delete canMove info

		/*---------- move pieces ----------*/
		setMove(move);

		/*---------- compare state and push changes ----------*/
		// only push data that have actually changed
		// on average, we need about 3.75 longs per position (instead of 7 if we back up all)
		// (hashCode, flags, 1/2 whites, 1 piece bb, plus sometimes another piece bb for captures, promotions, castles)
		int changeMask = 0;
		if (bakWhites != myBbWhites) {
			myBakStack[myBakIndex++] = bakWhites;
			changeMask++;
		}
		changeMask <<= 1;
		if (bakPawns != myBbPawns) {
			myBakStack[myBakIndex++] = bakPawns;
			changeMask++;
		}
		changeMask <<= 1;
		if (bakKnights != myBbKnights) {
			myBakStack[myBakIndex++] = bakKnights;
			changeMask++;
		}
		changeMask <<= 1;
		if (bakBishops != myBbBishops) {
			myBakStack[myBakIndex++] = bakBishops;
			changeMask++;
		}
		changeMask <<= 1;
		if (bakRooks != myBbRooks) {
			myBakStack[myBakIndex++] = bakRooks;
			changeMask++;
		}
		myBakStack[myBakIndex++] = (bakFlags << 5) | changeMask;
		myBakStack[myBakIndex] = 0L;

		if (PROFILE) {
			numLongsBackuped += numOfBitsSet(changeMask) + 2;
		}

		if (DEBUG) {
			System.out.println("I did a move " + Move.getString(move));
		}
	}

	@Override
	public boolean canUndoMove() {
		return myBakIndex > 0;
	}

	@Override
	public boolean undoMove() {
		if (PROFILE) {
			numUndoMove++;
		}

		if (myBakIndex > 0) {

			/*---------- reset pieces ----------*/
			long allFlags = myBakStack[--myBakIndex];
			int changeMask = (int) (allFlags & 0x1F);
			allFlags >>>= 5;

			int newChangeMask = 0;
			if ((changeMask & 1) != 0) {
				myBakStack[myBakIndex] = myBbRooks;
				myBbRooks = myBakStack[--myBakIndex];
				newChangeMask++;
			}
			changeMask >>>= 1;
			newChangeMask <<= 1;
			if ((changeMask & 1) != 0) {
				myBakStack[myBakIndex] = myBbBishops;
				myBbBishops = myBakStack[--myBakIndex];
				newChangeMask++;
			}
			changeMask >>>= 1;
			newChangeMask <<= 1;
			if ((changeMask & 1) != 0) {
				myBakStack[myBakIndex] = myBbKnights;
				myBbKnights = myBakStack[--myBakIndex];
				newChangeMask++;
			}
			changeMask >>>= 1;
			newChangeMask <<= 1;
			if ((changeMask & 1) != 0) {
				myBakStack[myBakIndex] = myBbPawns;
				myBbPawns = myBakStack[--myBakIndex];
				newChangeMask++;
			}
			changeMask >>>= 1;
			newChangeMask <<= 1;
			if ((changeMask & 1) != 0) {
				myBakStack[myBakIndex] = myBbWhites;
				myBbWhites = myBakStack[--myBakIndex];
				newChangeMask++;
			}
			myBakStack[myBakIndex] = myHashCode;
			myHashCode = myBakStack[--myBakIndex];
			myBakStack[myBakIndex] = getAllFlags(newChangeMask);

			myBlackKing = (int) (allFlags & 0x3F);
			allFlags >>>= 6;
			myWhiteKing = (int) (allFlags & 0x3F);
			allFlags >>>= 6;
			myFlags = allFlags;
			myBbBlacks = ((1L << myBlackKing) | myBbPawns | myBbKnights | myBbBishops | myBbRooks) & (~myBbWhites);

			myMoveStackIndex--;

			if (DEBUG) {
				System.out.println("Last move undone.");
			}
			return true;

		} else {
			return false;
		}
	}

	@Override
	public boolean canRedoMove() {
		return myBakIndex < myBakStack.length && myBakStack[myBakIndex] != 0;
	}

	@Override
	public boolean redoMove() {
		if (canRedoMove()) {

			/*---------- reset pieces ----------*/
			long allFlags = myBakStack[myBakIndex];
			int changeMask = (int) (allFlags & 0x1F);
			allFlags >>>= 5;

			int newChangeMask = 0;
			myBakStack[myBakIndex] = myHashCode;
			myHashCode = myBakStack[++myBakIndex];
			if ((changeMask & 1) != 0) {
				myBakStack[myBakIndex] = myBbWhites;
				myBbWhites = myBakStack[++myBakIndex];
				newChangeMask++;
			}
			changeMask >>>= 1;
			newChangeMask <<= 1;
			if ((changeMask & 1) != 0) {
				myBakStack[myBakIndex] = myBbPawns;
				myBbPawns = myBakStack[++myBakIndex];
				newChangeMask++;
			}
			changeMask >>>= 1;
			newChangeMask <<= 1;
			if ((changeMask & 1) != 0) {
				myBakStack[myBakIndex] = myBbKnights;
				myBbKnights = myBakStack[++myBakIndex];
				newChangeMask++;
			}
			changeMask >>>= 1;
			newChangeMask <<= 1;
			if ((changeMask & 1) != 0) {
				myBakStack[myBakIndex] = myBbBishops;
				myBbBishops = myBakStack[++myBakIndex];
				newChangeMask++;
			}
			changeMask >>>= 1;
			newChangeMask <<= 1;
			if ((changeMask & 1) != 0) {
				myBakStack[myBakIndex] = myBbRooks;
				myBbRooks = myBakStack[++myBakIndex];
				newChangeMask++;
			}
			myBakStack[myBakIndex++] = getAllFlags(newChangeMask);

			myBlackKing = (int) (allFlags & 0x3F);
			allFlags >>>= 6;
			myWhiteKing = (int) (allFlags & 0x3F);
			allFlags >>>= 6;
			myFlags = allFlags;
			myBbBlacks = ((1L << myBlackKing) | myBbPawns | myBbKnights | myBbBishops | myBbRooks) & (~myBbWhites);

			myMoveStackIndex++;

			if (DEBUG) {
				System.out.println("Last move redone.");
			}

			return true;
		} else {
			return false;
		}
	}

	/*
	 * =========================================================================
	 */

	@Override
	public Validity getValidity() {
		Validity val = super.getValidity();
		if (val != Validity.IS_VALID) {
			return val;
		}

		if (isKingNotToMoveAttacked()) {
			return Validity.WRONG_KING_ATTACKED;
		}
		if (isKingAttackedByUnmovedPawn()) {
			return Validity.KING_ATTACKED_BY_AN_UNMOVED_PAWN;
		}
		if (isKingAttackedByTwoPawns()) {
			return Validity.KING_ATTACKED_BY_TWO_PAWNS;
		}
		if (wasEnPassantDoubleStepIllegal()) {
			return Validity.INVALID_EN_PASSANT_SQUARE;
		}

		return Validity.IS_VALID;
	}

	/**
	 * The implementation partly overlaps with getValidity.
	 */
	@Override
	public boolean checkExtendedValidity() {
		if (!super.checkExtendedValidity()) {
			return false;
		}
		// First check the checks.
		int attackedKingSquare = getToPlay() == Chess.WHITE ? getWhitesKingSquare() : getBlacksKingSquare();
		long allAttackersBB = getAttackersBB(attackedKingSquare, getNotToPlay());
		int countAttackers = Long.bitCount(allAttackersBB);
		if (countAttackers > 2) { // There is not triple check.
			return false;
		}
		if (countAttackers > 0) { // Check double checks and checks of unmoved pawns.
			long diagonalAttackersBB = getDiagonalAttackers(attackedKingSquare, getNotToPlay(), false);
			long straightAttackersBB = getStraightAttackers(attackedKingSquare, getNotToPlay(), false);
			int countDiagonalAttackers = Long.bitCount(diagonalAttackersBB);
			int countStraightAttackers = Long.bitCount(straightAttackersBB);
			if (countDiagonalAttackers >= 2 || countStraightAttackers >= 2) {
				return false; // There is no double-check with two diagonal or straight attackers, resp.
			}

			int attacker1 = Chess.NO_PIECE;
			int attacker2 = Chess.NO_PIECE;
			for (int sqi = Chess.A1; sqi <= Chess.H8; ++sqi) {
				if ((allAttackersBB & (1L << sqi)) != 0L) {
					if (attacker1 == Chess.NO_PIECE) {
						attacker1 = getPiece(sqi);
					} else {
						attacker2 = getPiece(sqi);
						break;
					}
				}
			}
			if (attacker1 == attacker2) { // There is no double-check by the same kind of pieces.
				return false;
			}
			if (attacker1 == Chess.PAWN && attacker2 == Chess.KNIGHT) {
				return false;
			}
			if (attacker1 == Chess.KNIGHT && attacker2 == Chess.PAWN) {
				return false;
			}
			if (getToPlay() == Chess.WHITE) {
				if (Chess.sqiToRow(getWhitesKingSquare()) == 5 && (attacker1 == Chess.PAWN || attacker2 == Chess.PAWN)) {
					return false;
				}
			} else {
				if (Chess.sqiToRow(getBlacksKingSquare()) == 2 && (attacker1 == Chess.PAWN || attacker2 == Chess.PAWN)) {
					return false;
				}
			}
		}
		if (myVariant == Variant.STANDARD) {
			// Check bishops on first and eight rank.
			if (getStone(Chess.A1) == Chess.WHITE_BISHOP && getStone(Chess.B2) == Chess.WHITE_PAWN) {
				return false;
			}
			if (getStone(Chess.H1) == Chess.WHITE_BISHOP && getStone(Chess.G2) == Chess.WHITE_PAWN) {
				return false;
			}
			for (int sqi = Chess.B1; sqi <= Chess.G1; ++sqi) {
				if (sqi == Chess.C1 || sqi == Chess.F1) {
					continue;
				}
				if (getStone(sqi) == Chess.WHITE_BISHOP && getStone(sqi + 7) == Chess.WHITE_PAWN
						&& getStone(sqi + 9) == Chess.WHITE_PAWN) {
					return false;
				}
			}
			if (getStone(Chess.A8) == Chess.BLACK_BISHOP && getStone(Chess.B7) == Chess.BLACK_PAWN) {
				return false;
			}
			if (getStone(Chess.H8) == Chess.BLACK_BISHOP && getStone(Chess.G7) == Chess.BLACK_PAWN) {
				return false;
			}
			for (int sqi = Chess.B8; sqi <= Chess.G8; ++sqi) {
				if (sqi == Chess.C8 || sqi == Chess.F8) {
					continue;
				}
				if (getStone(sqi) == Chess.BLACK_BISHOP && getStone(sqi - 7) == Chess.BLACK_PAWN
						&& getStone(sqi - 9) == Chess.BLACK_PAWN) {
					return false;
				}
			}
		}
		// Check illegal pawn configurations.
		if (getStone(Chess.A2) == Chess.WHITE_PAWN && getStone(Chess.B2) == Chess.WHITE_PAWN
				&& getStone(Chess.A3) == Chess.WHITE_PAWN) {
			return false;
		}
		if (getStone(Chess.H2) == Chess.WHITE_PAWN && getStone(Chess.G2) == Chess.WHITE_PAWN
				&& getStone(Chess.H3) == Chess.WHITE_PAWN) {
			return false;
		}
		if (getStone(Chess.A7) == Chess.BLACK_PAWN && getStone(Chess.B7) == Chess.BLACK_PAWN
				&& getStone(Chess.A6) == Chess.BLACK_PAWN) {
			return false;
		}
		if (getStone(Chess.H7) == Chess.BLACK_PAWN && getStone(Chess.G7) == Chess.BLACK_PAWN
				&& getStone(Chess.H6) == Chess.BLACK_PAWN) {
			return false;
		}

		return true;
	}

	private boolean isKingNotToMoveAttacked() {
		int kingSquare = (getToPlay() == Chess.WHITE ? myBlackKing : myWhiteKing);
		return isAttacked(kingSquare, getToPlay(), 0L);
	}

	private boolean isKingAttackedByUnmovedPawn() {
		if (getToPlay() == Chess.WHITE) {
			int kingSquare = myWhiteKing;
			if (Chess.sqiToRow(kingSquare) == 5) {
				int col = Chess.sqiToCol(kingSquare);
				if (col == 0) { // king on a6
					return getStone(Chess.B7) == Chess.BLACK_PAWN;
				} else if (col == 7) { // king on h6
					return getStone(Chess.G7) == Chess.BLACK_PAWN;
				} else {
					return getStone(kingSquare + 7) == Chess.BLACK_PAWN || getStone(kingSquare + 9) == Chess.BLACK_PAWN;
				}
			}
		} else {
			int kingSquare = myBlackKing;
			if (Chess.sqiToRow(kingSquare) == 2) {
				int col = Chess.sqiToCol(kingSquare);
				if (col == 0) { // king on a3
					return getStone(Chess.B2) == Chess.WHITE_PAWN;
				} else if (col == 7) { //  king on h3
					return getStone(Chess.G2) == Chess.WHITE_PAWN;
				} else {
					return getStone(kingSquare - 7) == Chess.WHITE_PAWN || getStone(kingSquare - 9) == Chess.WHITE_PAWN;
				}
			}
		}
		return false;
	}

	private boolean isKingAttackedByTwoPawns() {
		if (getToPlay() == Chess.WHITE) {
			int kingSquare = myWhiteKing;
			int col = Chess.sqiToCol(kingSquare);
			if (col == 0 || col == 7) {
				return false;
			}
			int row = Chess.sqiToRow(kingSquare);
			if (row >= 6) {
				return false;
			}
			return getStone(kingSquare + 7) == Chess.BLACK_PAWN && getStone(kingSquare + 9) == Chess.BLACK_PAWN;
		} else {
			int kingSquare = myBlackKing;
			int col = Chess.sqiToCol(kingSquare);
			if (col == 0 || col == 7) {
				return false;
			}
			int row = Chess.sqiToRow(kingSquare);
			if (row <= 1) {
				return false;
			}
			return getStone(kingSquare - 7) == Chess.WHITE_PAWN && getStone(kingSquare - 9) == Chess.WHITE_PAWN;
		}
	}

	private boolean wasEnPassantDoubleStepIllegal() {
		// If an en passant square is set and a pawn is on the required square, it may still be that the double step 
		// of this pawn was not the last move, namely if the opponent's king is now attacked and this did not happen 
		// through that double step.
		if (getSqiEP() == Chess.NO_SQUARE) {
			return false;
		}
		// The implementation is according to ideas from getAllAttackers.

		int kingSquare = getToPlay() == Chess.BLACK ? getBlacksKingSquare() : getWhitesKingSquare();
		long bbAttackerPieces = (getToPlay() == Chess.BLACK ? myBbWhites : myBbBlacks);

		/*---------- knights ----------*/
		if ((KNIGHT_ATTACKS[kingSquare] & bbAttackerPieces & myBbKnights) != 0L) {
			// a knight attack is never caused by the double step
			return true;
		}

		int fromSquarePawn = getToPlay() == Chess.BLACK ? getSqiEP() - 8 : getSqiEP() + 8;
		long bbAllPieces = myBbWhites | myBbBlacks;

		/*---------- bishops ----------*/
		long bbTargets = BISHOP_ATTACKS[kingSquare] & myBbBishops & bbAttackerPieces;
		long bb = bbTargets;
		while (bb != 0L) {
			int from = getFirstSqi(bb);
			if (((1L << fromSquarePawn) & SQUARES_BETWEEN[from][kingSquare]) == 0L) { // an attack from behind the fromSquarePawn is fine
				if ((SQUARES_BETWEEN[from][kingSquare] & bbAllPieces & (~bbTargets)) == 0L) {
					return true;
				}
			}
			bb &= bb - 1;
		}

		/*---------- rooks -----------*/
		bbTargets = ROOK_ATTACKS[kingSquare] & myBbRooks & bbAttackerPieces;
		bb = bbTargets;
		while (bb != 0L) {
			int from = getFirstSqi(bb);
			if (((1L << fromSquarePawn) & SQUARES_BETWEEN[from][kingSquare]) == 0L) { // an attack from behind the fromSquarePawn is fine
				if ((SQUARES_BETWEEN[from][kingSquare] & bbAllPieces & (~bbTargets)) == 0L) {
					return true;
				}
			}
			bb &= bb - 1;
		}

		/*---------- pawns -----------*/
		if (getToPlay() == Chess.WHITE) {
			bbTargets = WHITE_PAWN_ATTACKS[kingSquare] & bbAttackerPieces & myBbPawns;
			bb = bbTargets;
			while (bb != 0L) {
				int from = getFirstSqi(bb);
				if (from != getSqiEP() - 8) {
					return true;
				}
				bb &= bb - 1;
			}
		} else {
			bbTargets = BLACK_PAWN_ATTACKS[kingSquare] & bbAttackerPieces & myBbPawns;
			bb = bbTargets;
			while (bb != 0L) {
				int from = getFirstSqi(bb);
				if (from != getSqiEP() + 8) {
					return true;
				}
				bb &= bb - 1;
			}
		}

		return false;
	}

	@Override
	public void internalValidate() throws IllegalPositionException {
		super.internalValidate();

		if (myWhiteKing < 0 || myWhiteKing >= Chess.NUM_OF_SQUARES) {
			throw new IllegalPositionException("White king square illegal: " + myWhiteKing);
		}
		if (myBlackKing < 0 || myBlackKing >= Chess.NUM_OF_SQUARES) {
			throw new IllegalPositionException("White king square illegal: " + myBlackKing);
		}

		int kingSquare = (getToPlay() == Chess.WHITE ? myBlackKing : myWhiteKing);
		if (isAttacked(kingSquare, getToPlay(), 0L)) {
			throw new IllegalPositionException("King " + Chess.sqiToStr(kingSquare) + " is in check without having the move.");
		}

		if (super.getHashCode() != getHashCode()) {
			System.out.println("Wrong hash code: " + getHashCode() + ". Should be: " + super.getHashCode() + ".");
			long diff = getHashCode() - super.getHashCode();
			System.out.println("Difference " + diff);
			for (int i = 0; i < Chess.NUM_OF_SQUARES; i++) {
				for (int j = 0; j < HASH_MOD[i].length; j++) {
					if (HASH_MOD[i][j] == diff) {
						System.out.println("Diff is sqi=" + i + " stone=" + (j - Chess.MIN_STONE));
					}
				}
			}
			for (int i = 0; i < 16; i++) {
				if (HASH_CASTLE_MOD[i] == diff) {
					System.out.println("Diff is castle " + i);
				}
			}
			for (int i = 0; i < 8; i++) {
				if (HASH_EP_MOD[i] == diff) {
					System.out.println("Diff is sqiEP " + i);
				}
			}
			System.out.println("PositionImpl.internalValidate: " + FEN.getFEN(this));
		}
	}

	/*
	 * =========================================================================
	 */

	@Override
	public boolean isCheck() {
		if (PROFILE) {
			numIsCheck++;
		}

		int cacheInfo = (int) (myFlags >>> CHECK_SHIFT) & CHECK_MASK;
		if (cacheInfo == FLAG_YES) {
			return true;
		} else if (cacheInfo == FLAG_NO) {
			return false;
		} else {
			boolean isCheck;
			if (getToPlay() == Chess.WHITE) {
				isCheck = isAttacked(myWhiteKing, Chess.BLACK, 0L);
			} else {
				isCheck = isAttacked(myBlackKing, Chess.WHITE, 0L);
			}
			myFlags &= ~(CHECK_MASK << CHECK_SHIFT);
			myFlags |= (isCheck ? FLAG_YES : FLAG_NO) << CHECK_SHIFT;
			return isCheck;
		}
	}

	@SuppressWarnings("unused")
	private boolean isTerminal() {
		return !canMove() || getHalfMoveClock() >= 100;
	}

	@Override
	public boolean isMate() {
		if (PROFILE) {
			numIsMate++;
		}

		return isCheck() && !canMove();
	}

	@Override
	public boolean isStaleMate() {
		if (PROFILE) {
			numIsStaleMate++;
		}

		return !isCheck() && !canMove();
	}

	@Override
	public boolean isInsufficientMaterial() {
		return myBbPawns == 0L && myBbRooks == 0L && Long.bitCount(myBbKnights) + Long.bitCount(myBbBishops) <= 1;
	}

	@Override
	public int getNumberOfPieces() {
		int numberOfPieces = Long.bitCount(myBbPawns) + Long.bitCount(myBbKnights); // pawns and knights
		numberOfPieces += Long.bitCount(myBbRooks) + Long.bitCount(myBbBishops) - Long.bitCount(myBbRooks & myBbBishops);
		// (rooks and queens) + (bishops and queens) - queens
		if (myWhiteKing != Chess.NO_SQUARE) {
			++numberOfPieces;
		}
		if (myBlackKing != Chess.NO_SQUARE) {
			++numberOfPieces;
		}
		return numberOfPieces;
	}

	/*
	 * =========================================================================
	 */

	@Override
	public short getLastShortMove() {
		return (myMoveStackIndex <= 0 ? Move.NO_MOVE : myMoveStack[myMoveStackIndex - 1]);
	}

	@Override
	public Move getLastMove() {
		if (myMoveStackIndex == 0) {
			return null;
		}
		short move = myMoveStack[myMoveStackIndex - 1];
		boolean wasWhiteMove = (getToPlay() == Chess.BLACK);
		if (Move.isCastle(move)) {
			return Move.createCastle(move, isCheck(), isMate(), wasWhiteMove);
		} else {
			int from = Move.getFromSqi(move);
			int to = Move.getToSqi(move);
			int piece = (Move.isPromotion(move) ? Chess.PAWN : getPiece(to));
			boolean isCapturing = Move.isCapturing(move);

			int colFrom = Chess.NO_COL;
			int rowFrom = Chess.NO_ROW;
			if (piece == Chess.PAWN) {
				if (isCapturing) {
					colFrom = Chess.sqiToCol(from);
				}
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

	@Override
	public String getLastMoveAsSanWithNumber() {
		int plies = getPlyNumber();
		if (plies > 0) {
			Move move = getLastMove();
			if (move == null) { // this can happen, if the start position is given by FEN
				return "";
			}
			if (plies % 2 == 1) {
				return (plies + 1) / 2 + ". " + move.getSAN();
			} else {
				return (plies + 1) / 2 + "... " + move.getSAN();
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
			if (DEBUG) {
				System.out.print("  trying from: " + from);
			}
			int pinnedDir = getPinnedDirection(from, getToPlay());
			if (attacks(from, to) && (pinnedDir == NO_DIR || areDirectionsParallel(pinnedDir, DIR[from][to]))) {
				if (DEBUG) {
					System.out.println(" ok");
				}
				return from;
			}
			bb &= bb - 1;
		}
		return Chess.NO_SQUARE;
	}

	@Override
	public Move getNextMove(short moveAsShort) {
		try {
			doMove(moveAsShort);
		} catch (IllegalMoveException e) {
			e.printStackTrace();
			return null;
		}
		Move move = getLastMove();
		undoMove();
		return move;
	}

	@Override
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

	@Override
	public short getPieceMove(int piece, int colFrom, int rowFrom, int to) {
		return Move.getRegularMove(getFromSqi(piece, colFrom, rowFrom, to), to, !isSquareEmpty(to));
	}

	/*
	 * TN: The following method is a part of PositionImpl::getLastMove and it shall
	 * be used nowhere else. So far it is not clear why undoMove() and everything
	 * until redoMove is necessary. However, if these operations are missing the
	 * build Move is invalid because of a NO_PIECE (and probably more problems).
	 * 
	 * One might think that this undo/redo is a performance issue, but even after
	 * more than one million calls within a real application, less than half a
	 * second was spent here.
	 */
	private Move getLastMovePiece(short move) throws IllegalMoveException {
		if (!Move.isValid(move)) {
			throw new IllegalMoveException(move);
		}

		undoMove();

		int from = Move.getFromSqi(move);
		int to = Move.getToSqi(move);
		boolean isCapturing = Move.isCapturing(move);
		int stone = getStone(from);

		int colFrom = Chess.NO_COL;
		int rowFrom = Chess.NO_ROW;

		long bb = getBitBoard(stone) & getAllAttackers(to, getToPlay(), false) & ~ofSquare(from);
		if (!isCapturing) {
			bb &= (~myBbPawns);
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

		redoMove(); // TN: doMove(move); is fine, too.
		return new Move(move, Chess.stoneToPiece(stone), colFrom, rowFrom, isCheck(), isMate(), getToPlay() == Chess.BLACK);
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
		if (PROFILE) {
			numGetPinnedDirection++;
		}

		int kingSqi = (color == Chess.WHITE ? myWhiteKing : myBlackKing);
		long bbSqi = ofSquare(sqi);

		if ((QUEEN_ATTACKS[kingSqi] & bbSqi) == 0L) {
			return NO_DIR;
		}

		int kingDir = DIR[kingSqi][sqi];
		long kingDirRim = RIM_BOARD[kingDir];
		if ((kingDirRim & bbSqi) != 0L) {
			return NO_DIR; // there is nothing behind piece
		}

		long bbTarget;
		if (isDiagonal(kingDir)) {
			bbTarget = BISHOP_ATTACKS[kingSqi] & myBbBishops & (color == Chess.WHITE ? myBbBlacks : myBbWhites);
		} else {
			bbTarget = ROOK_ATTACKS[kingSqi] & myBbRooks & (color == Chess.WHITE ? myBbBlacks : myBbWhites);
		}
		if (bbTarget == 0L) {
			return NO_DIR;
		}

		long bbAllPieces = myBbWhites | myBbBlacks;
		if ((SQUARES_BETWEEN[kingSqi][sqi] & bbAllPieces) != 0L) {
			return NO_DIR;
		}

		// System.out.println("now checking behind sqi");
		long bb = bbSqi;
		int vector = DIR_SHIFT[kingDir];
		do {
			// bb not on rim checked above -> can increment without test
			if (vector < 0) {
				bb >>>= -vector;
			} else {
				bb <<= vector;
			}
			if ((bbTarget & bb) != 0L) {
				return kingDir;
			}
			if ((bbAllPieces & bb) != 0L) {
				return NO_DIR;
			}
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
		case Chess.NO_PIECE -> {
			return false;
		}
		case Chess.PAWN -> {
			if (getToPlay() == Chess.WHITE) {
				return (WHITE_PAWN_ATTACKS[from] & bbTo) != 0;
			} else {
				return (BLACK_PAWN_ATTACKS[from] & bbTo) != 0;
			}
		}
		case Chess.KNIGHT -> {
			return (KNIGHT_ATTACKS[from] & bbTo) != 0;
		}
		case Chess.KING -> {
			return (KING_ATTACKS[from] & bbTo) != 0;
		}
		case Chess.BISHOP, Chess.ROOK, Chess.QUEEN -> {
			if ((piece == Chess.BISHOP && (BISHOP_ATTACKS[from] & bbTo) == 0)
					|| (piece == Chess.ROOK && (ROOK_ATTACKS[from] & bbTo) == 0)) {
				return false;
			}
			if (piece == Chess.QUEEN && (QUEEN_ATTACKS[from] & bbTo) == 0) {
				return false;
			}
			long bbFrom = ofSquare(from);
			int vector = DIR_SHIFT[DIR[from][to]];
			if (vector < 0) {
				bbFrom >>>= -vector;
			} else {
				bbFrom <<= vector;
			}
			while (bbFrom != bbTo) {
				if (((myBbWhites | myBbBlacks) & bbFrom) != 0) {
					return false;
				}
				if (vector < 0) {
					bbFrom >>>= -vector;
				} else {
					bbFrom <<= vector;
				}
			}
			return true;
		}
		default -> throw new RuntimeException("Illegal piece: " + piece);
		}
	}

	/*
	 * =========================================================================
	 */

	private boolean isAttacked(int sqi, int attacker, long bbExclude) {
		if (PROFILE) {
			numIsAttacked++;
		}

		// only to print sqi, otherwise not needed
		if (sqi < 0 || sqi > 63) {
			throw new IllegalArgumentException("Illegal sqi: " + sqi);
		}

		long bbAttackerPieces = (attacker == Chess.WHITE ? myBbWhites : myBbBlacks) & (~bbExclude);
		long bbAllPieces = (myBbWhites | myBbBlacks) & (~bbExclude);

		/*---------- knights ----------*/
		if ((KNIGHT_ATTACKS[sqi] & bbAttackerPieces & myBbKnights) != 0) {
			return true;
		}

		/*---------- sliding pieces ----------*/
		long bbTargets = ((BISHOP_ATTACKS[sqi] & myBbBishops) | (ROOK_ATTACKS[sqi] & myBbRooks)) & bbAttackerPieces;
		while (bbTargets != 0L) {
			int from = getFirstSqi(bbTargets);
			if ((SQUARES_BETWEEN[from][sqi] & bbAllPieces) == 0L) {
				return true;
			}
			bbTargets &= bbTargets - 1;
		}

		/*---------- king & pawns ----------*/
		if (attacker == Chess.WHITE) {
			// inverse -> black_pawn_attacks
			if (((BLACK_PAWN_ATTACKS[sqi] & bbAttackerPieces & myBbPawns) != 0)
					|| ((KING_ATTACKS[sqi] & ofSquare(myWhiteKing) & (~bbExclude)) != 0)) {
				return true;
			}
		} else {
			if (((WHITE_PAWN_ATTACKS[sqi] & bbAttackerPieces & myBbPawns) != 0)
					|| ((KING_ATTACKS[sqi] & ofSquare(myBlackKing) & (~bbExclude)) != 0)) {
				return true;
			}
		}

		return false;
	}

	private long getAllAttackers(int sqi, int color, boolean includeInbetweenSquares) {
		if (PROFILE) {
			++numAllAttackers;
		}

		long attackers = 0L;
		long bbAttackerPieces = (color == Chess.WHITE ? myBbWhites : myBbBlacks);
		long bbAllPieces = myBbWhites | myBbBlacks;

		/*---------- knights ----------*/
		attackers |= KNIGHT_ATTACKS[sqi] & bbAttackerPieces & myBbKnights;

		/*---------- sliding pieces ----------*/
		long bbTargets = ((BISHOP_ATTACKS[sqi] & myBbBishops) | (ROOK_ATTACKS[sqi] & myBbRooks)) & bbAttackerPieces;
		while (bbTargets != 0L) {
			int from = getFirstSqi(bbTargets);
			long squaresInBetween = SQUARES_BETWEEN[from][sqi];
			if ((squaresInBetween & bbAllPieces) == 0L) {
				attackers |= ofSquare(from);
				if (includeInbetweenSquares) {
					attackers |= squaresInBetween;
				}
			}
			bbTargets &= bbTargets - 1;
		}

		/*---------- pawns & king ----------*/
		if (color == Chess.WHITE) {
			// inverse -> black_pawn_attacks
			attackers |= BLACK_PAWN_ATTACKS[sqi] & bbAttackerPieces & myBbPawns;
			attackers |= KING_ATTACKS[sqi] & ofSquare(myWhiteKing);
			if (sqi == getSqiEP()) {
				attackers |= BLACK_PAWN_ATTACKS[sqi - Chess.NUM_OF_COLS] & bbAttackerPieces & myBbPawns;
			}
		} else {
			attackers |= WHITE_PAWN_ATTACKS[sqi] & bbAttackerPieces & myBbPawns;
			attackers |= KING_ATTACKS[sqi] & ofSquare(myBlackKing);
			if (sqi == getSqiEP()) {
				attackers |= WHITE_PAWN_ATTACKS[sqi + Chess.NUM_OF_COLS] & bbAttackerPieces & myBbPawns;
			}
		}

		return attackers;
	}

	/*
	 * Count only diagonal attackers: queens, bishops and pawns. 
	 */
	private long getDiagonalAttackers(int sqi, int color, boolean includeInbetweenSquares) {
		long attackers = 0L;
		long bbAttackerPieces = (color == Chess.WHITE ? myBbWhites : myBbBlacks);
		long bbAllPieces = myBbWhites | myBbBlacks;

		/*---------- sliding pieces ----------*/
		long bbTargets = (BISHOP_ATTACKS[sqi] & myBbBishops) & bbAttackerPieces;
		while (bbTargets != 0L) {
			int from = getFirstSqi(bbTargets);
			long squaresInBetween = SQUARES_BETWEEN[from][sqi];
			if ((squaresInBetween & bbAllPieces) == 0L) {
				attackers |= ofSquare(from);
				if (includeInbetweenSquares) {
					attackers |= squaresInBetween;
				}
			}
			bbTargets &= bbTargets - 1;
		}

		/*---------- pawns ----------------*/
		if (color == Chess.WHITE) {
			// inverse -> black_pawn_attacks
			attackers |= BLACK_PAWN_ATTACKS[sqi] & bbAttackerPieces & myBbPawns;
			if (sqi == getSqiEP()) {
				attackers |= BLACK_PAWN_ATTACKS[sqi - Chess.NUM_OF_COLS] & bbAttackerPieces & myBbPawns;
			}
		} else {
			attackers |= WHITE_PAWN_ATTACKS[sqi] & bbAttackerPieces & myBbPawns;
			if (sqi == getSqiEP()) {
				attackers |= WHITE_PAWN_ATTACKS[sqi + Chess.NUM_OF_COLS] & bbAttackerPieces & myBbPawns;
			}
		}

		return attackers;
	}

	/*
	 * Count only vertical and horizontal attackers: queens and rooks. 
	 */
	private long getStraightAttackers(int sqi, int color, boolean includeInbetweenSquares) {
		long attackers = 0L;
		long bbAttackerPieces = (color == Chess.WHITE ? myBbWhites : myBbBlacks);
		long bbAllPieces = myBbWhites | myBbBlacks;

		/*---------- sliding pieces ----------*/
		long bbTargets = (ROOK_ATTACKS[sqi] & myBbRooks) & bbAttackerPieces;
		while (bbTargets != 0L) {
			int from = getFirstSqi(bbTargets);
			long squaresInBetween = SQUARES_BETWEEN[from][sqi];
			if ((squaresInBetween & bbAllPieces) == 0L) {
				attackers |= ofSquare(from);
				if (includeInbetweenSquares) {
					attackers |= squaresInBetween;
				}
			}
			bbTargets &= bbTargets - 1;
		}
		return attackers;
	}

	private int getAllKnightMoves(int moveIndex, long bbTargets) {
		if (bbTargets == 0L) {
			return moveIndex;
		}

		long bbToPlay = (getToPlay() == Chess.WHITE ? myBbWhites : myBbBlacks);

		/*---------- knights moves ----------*/
		long bbPieces = myBbKnights & bbToPlay;
		while (bbPieces != 0L) {
			int from = getFirstSqi(bbPieces);
			if (getPinnedDirection(from, getToPlay()) == NO_DIR) {
				long destSquares = KNIGHT_ATTACKS[from] & (~bbToPlay) & bbTargets;
				while (destSquares != 0L) {
					if (moveIndex == -1) {
						return 1;
					}
					int to = getFirstSqi(destSquares);
					allMoves[moveIndex++] = Move.getRegularMove(from, to, !isSquareEmpty(to));
					destSquares &= destSquares - 1;
				}
			}
			bbPieces &= bbPieces - 1;
		}
		return moveIndex;
	}

	// TN: A method for bishop, queen and rook moves
	private int getAllSlidingMoves(int moveIndex, long bbTargets, long bbPieces, int piece) {
		if (bbTargets == 0L) {
			return moveIndex;
		}

		long bbToPlay = (getToPlay() == Chess.WHITE ? myBbWhites : myBbBlacks);
		long bbNotToPlay = (getToPlay() == Chess.WHITE ? myBbBlacks : myBbWhites);

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
							if (dirShift < 0) {
								bb >>>= -dirShift;
							} else {
								bb <<= dirShift;
							}
							to += dirShift;
							if ((bb & bbToPlay) != 0L) {
								break;
							}
							if ((bb & bbTargets) != 0L) {
								if (moveIndex == -1) {
									return 1;
								}
								if ((bb & bbNotToPlay) == 0L) {
									allMoves[moveIndex++] = Move.getRegularMove(from, to, false);
								} else {
									allMoves[moveIndex++] = Move.getRegularMove(from, to, true);
									break;
								}
							} else if ((bb & bbNotToPlay) != 0) {
								break;
							}
						}
					}
				}
			}
			bbPieces &= bbPieces - 1;
		}
		return moveIndex;
	}

	private int getAllKingMoves(int moveIndex, long bbTargets, boolean withCastles) {
		if (bbTargets == 0L) {
			return moveIndex;
		}

		long bbToPlay = (getToPlay() == Chess.WHITE ? myBbWhites : myBbBlacks);
		long bbAllPieces = myBbWhites | myBbBlacks;

		/*---------- regular king moves ----------*/
		int from = (getToPlay() == Chess.WHITE ? myWhiteKing : myBlackKing);
		long bbFrom = ofSquare(from);
		long destSquares = KING_ATTACKS[from] & (~bbToPlay) & bbTargets;
		while (destSquares != 0L) {
			int to = getFirstSqi(destSquares);
			if (!isAttacked(to, getNotToPlay(), bbFrom)) {
				if (moveIndex == -1) {
					return 1;
				}
				allMoves[moveIndex++] = Move.getRegularMove(from, to, !isSquareEmpty(to));
			}
			destSquares &= destSquares - 1;
		}

		/*---------- castles ----------*/
		if (withCastles) {
			int castles = getCastles();
			if (myVariant == Variant.STANDARD) {
				if (getToPlay() == Chess.WHITE) {
					// don't need to exclude anything for isAttack since other check would fail in those cases
					if ((castles & WHITE_SHORT_CASTLE) != 0 && (ofSquare(Chess.G1) & bbTargets) != 0L
							&& (bbAllPieces & WHITE_SHORT_CASTLE_EMPTY_MASK) == 0L && !isAttacked(Chess.F1, Chess.BLACK, 0L)
							&& !isAttacked(Chess.G1, Chess.BLACK, 0L)) {
						if (moveIndex == -1) {
							return 1;
						}
						allMoves[moveIndex++] = Move.WHITE_SHORT_CASTLE;
					}
					if ((castles & WHITE_LONG_CASTLE) != 0 && (ofSquare(Chess.C1) & bbTargets) != 0L
							&& (bbAllPieces & WHITE_LONG_CASTLE_EMPTY_MASK) == 0L && !isAttacked(Chess.D1, Chess.BLACK, 0L)
							&& !isAttacked(Chess.C1, Chess.BLACK, 0L)) {
						if (moveIndex == -1) {
							return 1;
						}
						allMoves[moveIndex++] = Move.WHITE_LONG_CASTLE;
					}
				} else {
					if ((castles & BLACK_SHORT_CASTLE) != 0 && (ofSquare(Chess.G8) & bbTargets) != 0L
							&& (bbAllPieces & BLACK_SHORT_CASTLE_EMPTY_MASK) == 0L && !isAttacked(Chess.F8, Chess.WHITE, 0L)
							&& !isAttacked(Chess.G8, Chess.WHITE, 0L)) {
						if (moveIndex == -1) {
							return 1;
						}
						allMoves[moveIndex++] = Move.BLACK_SHORT_CASTLE;
					}
					if ((castles & BLACK_LONG_CASTLE) != 0 && (ofSquare(Chess.C8) & bbTargets) != 0L
							&& (bbAllPieces & BLACK_LONG_CASTLE_EMPTY_MASK) == 0L && !isAttacked(Chess.D8, Chess.WHITE, 0L)
							&& !isAttacked(Chess.C8, Chess.WHITE, 0L)) {
						if (moveIndex == -1) {
							return 1;
						}
						allMoves[moveIndex++] = Move.BLACK_LONG_CASTLE;
					}
				}
			} else { // Chess960
				if (getToPlay() == Chess.WHITE) {
					if (checkChess960WhiteShortCastle(bbAllPieces, bbTargets)) {
						if (moveIndex == -1) {
							return 1;
						}
						allMoves[moveIndex++] = Move.getChess960Castle(Chess.WHITE, getChess960KingFile(),
								getChess960KingsideRookFile());
					}
					if (checkChess960WhiteLongCastle(bbAllPieces, bbTargets)) {
						if (moveIndex == -1) {
							return 1;
						}
						allMoves[moveIndex++] = Move.getChess960Castle(Chess.WHITE, getChess960KingFile(),
								getChess960QueensideRookFile());
					}
				} else {
					if (checkChess960BlackShortCastle(bbAllPieces, bbTargets)) {
						if (moveIndex == -1) {
							return 1;
						}
						allMoves[moveIndex++] = Move.getChess960Castle(Chess.BLACK, getChess960KingFile(),
								getChess960KingsideRookFile());
					}
					if (checkChess960BlackLongCastle(bbAllPieces, bbTargets)) {
						if (moveIndex == -1) {
							return 1;
						}
						allMoves[moveIndex++] = Move.getChess960Castle(Chess.BLACK, getChess960KingFile(),
								getChess960QueensideRookFile());
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
		if ((getCastles() & WHITE_SHORT_CASTLE) == 0 || (ofSquare(Chess.G1) & bbTargets) == 0L
				|| !checkChess960KingCastleCondition(bbAllPieces, getChess960KingFile() + 1, Chess.G1,
						getChess960KingsideRookFile(), Chess.BLACK)) {
			return false;
		}
		if (getChess960KingsideRookFile() < Chess.F1) {
			if (!checkChess960RookCastleCondition(bbAllPieces, getChess960KingsideRookFile() + 1, Chess.F1,
					getChess960KingFile())) {
				return false;
			}
		} else if (getChess960KingsideRookFile() > Chess.F1) {
			if (!checkChess960RookCastleCondition(bbAllPieces, Chess.F1, getChess960KingsideRookFile() - 1,
					getChess960KingFile())) {
				return false;
			}
		}
		return true;
	}

	private boolean checkChess960WhiteLongCastle(long bbAllPieces, long bbTargets) {
		if ((getCastles() & WHITE_LONG_CASTLE) == 0 || (ofSquare(Chess.C1) & bbTargets) == 0L) {
			return false;
		}
		if (getChess960KingFile() < Chess.C1) {
			if (!checkChess960KingCastleCondition(bbAllPieces, getChess960KingFile() + 1, Chess.C1,
					getChess960QueensideRookFile(), Chess.BLACK)) {
				return false;
			}
		} else {
			if (!checkChess960KingCastleCondition(bbAllPieces, Chess.C1, getChess960KingFile() - 1,
					getChess960QueensideRookFile(), Chess.BLACK)) {
				return false;
			}
		}
		if (getChess960QueensideRookFile() < Chess.D1) {
			if (!checkChess960RookCastleCondition(bbAllPieces, getChess960QueensideRookFile() + 1, Chess.D1,
					getChess960KingFile())) {
				return false;
			}
		} else if (getChess960QueensideRookFile() > Chess.D1) {
			if (!checkChess960RookCastleCondition(bbAllPieces, Chess.D1, getChess960QueensideRookFile() - 1,
					getChess960KingFile())) {
				return false;
			}
		}
		return true;
	}

	private boolean checkChess960BlackShortCastle(long bbAllPieces, long bbTargets) {
		if ((getCastles() & BLACK_SHORT_CASTLE) == 0 || (ofSquare(Chess.G8) & bbTargets) == 0L
				|| !checkChess960KingCastleCondition(bbAllPieces, Chess.A8 + getChess960KingFile() + 1, Chess.A8 + Chess.G1,
						Chess.A8 + getChess960KingsideRookFile(), Chess.WHITE)) {
			return false;
		}
		if (Chess.A8 + getChess960KingsideRookFile() < Chess.F8) {
			if (!checkChess960RookCastleCondition(bbAllPieces, Chess.A8 + getChess960KingsideRookFile() + 1, Chess.F8,
					Chess.A8 + getChess960KingFile())) {
				return false;
			}
		} else if (Chess.A8 + getChess960KingsideRookFile() > Chess.F8) {
			if (!checkChess960RookCastleCondition(bbAllPieces, Chess.F8, Chess.A8 + getChess960KingsideRookFile() - 1,
					Chess.A8 + getChess960KingFile())) {
				return false;
			}
		}
		return true;
	}

	private boolean checkChess960BlackLongCastle(long bbAllPieces, long bbTargets) {
		if ((getCastles() & BLACK_LONG_CASTLE) == 0 || (ofSquare(Chess.C8) & bbTargets) == 0L) {
			return false;
		}
		if (Chess.A8 + getChess960KingFile() < Chess.C8) {
			if (!checkChess960KingCastleCondition(bbAllPieces, Chess.A8 + getChess960KingFile() + 1, Chess.C8,
					Chess.A8 + getChess960QueensideRookFile(), Chess.WHITE)) {
				return false;
			}
		} else {
			if (!checkChess960KingCastleCondition(bbAllPieces, Chess.C8, Chess.A8 + getChess960KingFile() - 1,
					Chess.A8 + getChess960QueensideRookFile(), Chess.WHITE)) {
				return false;
			}
		}
		if (Chess.A8 + getChess960QueensideRookFile() < Chess.D8) {
			if (!checkChess960RookCastleCondition(bbAllPieces, Chess.A8 + getChess960QueensideRookFile() + 1, Chess.D8,
					Chess.A8 + getChess960KingFile())) {
				return false;
			}
		} else if (Chess.A8 + getChess960QueensideRookFile() > Chess.D8) {
			if (!checkChess960RookCastleCondition(bbAllPieces, Chess.D8, Chess.A8 + getChess960QueensideRookFile() - 1,
					Chess.A8 + getChess960KingFile())) {
				return false;
			}
		}
		return true;
	}

	private int getAllPawnMoves(int moveIndex, long bbTargets) {
		if (bbTargets == 0L) {
			return moveIndex;
		}

		long bbToPlay, bbNotToPlay;
		int pawnMoveDir, secondRank, eighthRank;

		if (getToPlay() == Chess.WHITE) {
			bbToPlay = myBbWhites;
			bbNotToPlay = myBbBlacks;
			pawnMoveDir = N;
			secondRank = 1;
			eighthRank = 7;
		} else {
			bbToPlay = myBbBlacks;
			bbNotToPlay = myBbWhites;
			pawnMoveDir = S;
			secondRank = 6;
			eighthRank = 0;
		}

		// if the pawn belonging to ep square is a target, include ep square as target
		int sqiEP = getSqiEP();
		if (getSqiEP() != Chess.NO_SQUARE) {
			int epPawnSqi = sqiEP + (getToPlay() == Chess.WHITE ? -Chess.NUM_OF_COLS : Chess.NUM_OF_COLS);
			// TN: Old: if ((bbTargets & ofSquare(epPawnSqi)) != 0) {
			if ((bbTargets & ofSquare(epPawnSqi)) != 0 && (bbNotToPlay & myBbPawns & ofSquare(epPawnSqi)) != 0) {
				// TN: Seybold's comments are unclear.
				bbTargets |= ofSquare(sqiEP); // pawn cannot move on ep square
				// without capturing (blocked by ep pawn), so adding it is safe
				bbNotToPlay |= ofSquare(sqiEP); // to prevent the ep square from being filtered
			}
		}

		long bbPieces = myBbPawns & bbToPlay;
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
							if (moveIndex == -1) {
								return 1;
							}
							allMoves[moveIndex++] = Move.getPawnMove(from, to, false, Chess.QUEEN);
							allMoves[moveIndex++] = Move.getPawnMove(from, to, false, Chess.ROOK);
							allMoves[moveIndex++] = Move.getPawnMove(from, to, false, Chess.BISHOP);
							allMoves[moveIndex++] = Move.getPawnMove(from, to, false, Chess.KNIGHT);
						}
					} else {
						if ((bbTo & bbTargets) != 0L) {
							if (moveIndex == -1) {
								return 1;
							}
							allMoves[moveIndex++] = Move.getPawnMove(from, to, false, Chess.NO_PIECE);
						}
						if (Chess.sqiToRow(from) == secondRank) {
							to += DIR_SHIFT[pawnMoveDir];
							// no need to check is pinned again, since double
							// steps are always possible
							// if single steps are
							if (isSquareEmpty(to) && (ofSquare(to) & bbTargets) != 0L) {
								if (moveIndex == -1) {
									return 1;
								}
								allMoves[moveIndex++] = Move.getPawnMove(from, to, false, Chess.NO_PIECE);
							}
						}
					}
				}
			}

			/*---------- pawn capture ----------*/
			long destSquares = (getToPlay() == Chess.WHITE ? WHITE_PAWN_ATTACKS[from] : BLACK_PAWN_ATTACKS[from]) & bbTargets;
			destSquares &= bbNotToPlay;

			while (destSquares != 0L) {
				to = getFirstSqi(destSquares);
				int dir = DIR[from][to];
				if (pinnedDir == NO_DIR || dir == NO_DIR || areDirectionsParallel(pinnedDir, dir)) {
					if (moveIndex == -1) {
						return 1;
					}
					// int piece = getPiece(to);
					if (Chess.sqiToRow(to) == eighthRank) {
						allMoves[moveIndex++] = Move.getPawnMove(from, to, true, Chess.QUEEN);
						allMoves[moveIndex++] = Move.getPawnMove(from, to, true, Chess.ROOK);
						allMoves[moveIndex++] = Move.getPawnMove(from, to, true, Chess.BISHOP);
						allMoves[moveIndex++] = Move.getPawnMove(from, to, true, Chess.KNIGHT);
					} else if (to == sqiEP && isSquareEmpty(sqiEP)) {
						allMoves[moveIndex++] = Move.getEPMove(from, to);
						myEnPassantFlag = true;
					} else {
						allMoves[moveIndex++] = Move.getPawnMove(from, to, true, Chess.NO_PIECE);
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
		long bbTargets = getToPlay() == Chess.WHITE ? myBbBlacks : myBbWhites;
		// can include sqiEP safely since no pawn can move on sqi if it is set
		long bbPawnTargets = (getSqiEP() == Chess.NO_SQUARE ? bbTargets : bbTargets | ofSquare(getSqiEP()));
		return getAllMoves(bbTargets, bbPawnTargets);
	}

	public short[] getAllNonCapturingMoves() {
		long bbTargets = getToPlay() == Chess.WHITE ? ~myBbBlacks : ~myBbWhites;
		// can exclude sqiEP safely since no pawn can move on sqi if it is set
		long bbPawnTargets = (getSqiEP() == Chess.NO_SQUARE ? bbTargets : bbTargets & (~ofSquare(getSqiEP())));
		return getAllMoves(bbTargets, bbPawnTargets);
	}

	private short[] getAllMoves(long bbTargets, long bbPawnTargets) {
		if (PROFILE) {
			numGetAllMoves++;
		}

		if (bbTargets == 0L) {
			return new short[0];
		}

		int moveIndex = 0;

		long bbToPlay = (getToPlay() == Chess.WHITE ? myBbWhites : myBbBlacks);
		if (isCheck()) {
			moveIndex = getAllKingMoves(moveIndex, bbTargets, false);
			long attackers = getAllAttackers((getToPlay() == Chess.WHITE ? myWhiteKing : myBlackKing), getNotToPlay(), false);
			if (isExactlyOneBitSet(attackers)) {
				attackers = getAllAttackers((getToPlay() == Chess.WHITE ? myWhiteKing : myBlackKing), getNotToPlay(), true);
				bbTargets &= attackers;
				bbPawnTargets &= attackers;
				moveIndex = getAllKnightMoves(moveIndex, bbTargets);
				moveIndex = getAllSlidingMoves(moveIndex, bbTargets, myBbBishops & (~myBbRooks) & bbToPlay, Chess.BISHOP);
				moveIndex = getAllSlidingMoves(moveIndex, bbTargets, myBbRooks & (~myBbBishops) & bbToPlay, Chess.ROOK);
				moveIndex = getAllSlidingMoves(moveIndex, bbTargets, myBbRooks & myBbBishops & bbToPlay, Chess.QUEEN);
				moveIndex = getAllPawnMoves(moveIndex, bbPawnTargets);
			}
		} else {
			moveIndex = getAllKnightMoves(moveIndex, bbTargets);
			moveIndex = getAllSlidingMoves(moveIndex, bbTargets, myBbBishops & (~myBbRooks) & bbToPlay, Chess.BISHOP);
			moveIndex = getAllSlidingMoves(moveIndex, bbTargets, myBbRooks & (~myBbBishops) & bbToPlay, Chess.ROOK);
			moveIndex = getAllSlidingMoves(moveIndex, bbTargets, myBbRooks & myBbBishops & bbToPlay, Chess.QUEEN);
			moveIndex = getAllKingMoves(moveIndex, bbTargets, true);
			moveIndex = getAllPawnMoves(moveIndex, bbPawnTargets);
		}

		short[] onlyTheMoves = new short[moveIndex];
		System.arraycopy(allMoves, 0, onlyTheMoves, 0, moveIndex);

		return onlyTheMoves;
	}

	private boolean canMove() {
		int cacheInfo = (int) (myFlags >>> CAN_MOVE_SHIFT) & CAN_MOVE_MASK;
		if (cacheInfo == FLAG_YES) {
			return true;
		} else if (cacheInfo == FLAG_NO) {
			return false;
		} else {
			boolean canMove = false;
			long bbToPlay = (getToPlay() == Chess.WHITE ? myBbWhites : myBbBlacks);
			if (isCheck()) {
				if (getAllKingMoves(-1, ~0L, false) > 0) {
					canMove = true;
				} else {
					long attackers = getAllAttackers((getToPlay() == Chess.WHITE ? myWhiteKing : myBlackKing), getNotToPlay(),
							false);
					if (isExactlyOneBitSet(attackers)) {
						attackers = getAllAttackers((getToPlay() == Chess.WHITE ? myWhiteKing : myBlackKing), getNotToPlay(),
								true);
						canMove = (getAllKnightMoves(-1, attackers) > 0) || (getAllPawnMoves(-1, attackers) > 0)
								|| (getAllSlidingMoves(-1, attackers, myBbBishops & (~myBbRooks) & bbToPlay, Chess.BISHOP) > 0)
								|| (getAllSlidingMoves(-1, attackers, myBbRooks & (~myBbBishops) & bbToPlay, Chess.ROOK) > 0)
								|| (getAllSlidingMoves(-1, attackers, myBbRooks & myBbBishops & bbToPlay, Chess.QUEEN) > 0);
					}
				}
			} else {
				long bbTargets = ~0L;
				canMove = (getAllKnightMoves(-1, bbTargets) > 0) || (getAllPawnMoves(-1, bbTargets) > 0)
						|| (getAllSlidingMoves(-1, bbTargets, myBbBishops & (~myBbRooks) & bbToPlay, Chess.BISHOP) > 0)
						|| (getAllSlidingMoves(-1, bbTargets, myBbRooks & (~myBbBishops) & bbToPlay, Chess.ROOK) > 0)
						|| (getAllSlidingMoves(-1, bbTargets, myBbRooks & myBbBishops & bbToPlay, Chess.QUEEN) > 0)
						|| (getAllKingMoves(-1, bbTargets, false) > 0);
				// don't test castling since it cannot be the only move
			}
			myFlags &= ~(CAN_MOVE_MASK << CAN_MOVE_SHIFT);
			myFlags |= (canMove ? FLAG_YES : FLAG_NO) << CAN_MOVE_SHIFT;
			return canMove;
		}
	}

	/*
	 * =========================================================================
	 */

	@SuppressWarnings("unused")
	private int getMaterial() {
		int value = 0;
		value += 100 * (numOfBitsSet(myBbPawns & myBbWhites) - numOfBitsSet(myBbPawns & myBbBlacks));
		value += 300 * (numOfBitsSet(myBbKnights & myBbWhites) - numOfBitsSet(myBbKnights & myBbBlacks));
		value += 325
				* (numOfBitsSet(myBbBishops & (~myBbRooks) & myBbWhites) - numOfBitsSet(myBbBishops & (~myBbRooks) & myBbBlacks));
		value += 500
				* (numOfBitsSet(myBbRooks & (~myBbBishops) & myBbWhites) - numOfBitsSet(myBbRooks & (~myBbBishops) & myBbBlacks));
		value += 900 * (numOfBitsSet(myBbRooks & myBbBishops & myBbWhites) - numOfBitsSet(myBbRooks & myBbBishops & myBbBlacks));
		return (getToPlay() == Chess.WHITE ? value : -value);
	}

	@Override
	public Variant getVariant() {
		return myVariant;
	}

	@Override
	public void setChess960() {
		myVariant = Variant.CHESS960;
	}

	/**
	 * There are two possible kind of values for the arguments possible.
	 * 1. Chess.A_FILE, ... Chess.H_FILE, or
	 * 2. Chess.A1, ... , Chess.H8, and in this case the square is used to define the file value under 1.
	 * So, the second method is for convenience.
	 * 
	 * Note that the information set with this method and the information set with setCastles(int)
	 * can be inconsistent. So, make sure that you use them with care.
	 */
	@Override
	public void setChess960CastlingFiles(int kingFile, int queensideRookFile, int kingsideRookFile) {
		if (kingFile != Chess.NO_FILE) {
			myChess960CastlingFiles |= 1 << CHESS960_KING_FILE_FLAG_SHIFT;
			myChess960CastlingFiles &= ~CHESS960_KING_FILE_MASK;
			myChess960CastlingFiles |= (kingFile % 8) << CHESS960_KING_FILE_SHIFT;
		} else {
			myChess960CastlingFiles &= ~(1 << CHESS960_KING_FILE_FLAG_SHIFT);
		}
		if (queensideRookFile != Chess.NO_FILE) {
			myChess960CastlingFiles |= 1 << CHESS960_QUEENSIDE_ROOK_FILE_FLAG_SHIFT;
			myChess960CastlingFiles &= ~CHESS960_QUEENSIDE_ROOK_FILE_MASK;
			myChess960CastlingFiles |= (queensideRookFile % 8) << CHESS960_QUEENSIDE_ROOK_FILE_SHIFT;
		} else {
			myChess960CastlingFiles &= ~(1 << CHESS960_QUEENSIDE_ROOK_FILE_FLAG_SHIFT);
		}
		if (kingsideRookFile != Chess.NO_FILE) {
			myChess960CastlingFiles |= 1 << CHESS960_KINGSIDE_ROOK_FILE_FLAG_SHIFT;
			myChess960CastlingFiles &= ~CHESS960_KINGSIDE_ROOK_FILE_MASK;
			myChess960CastlingFiles |= (kingsideRookFile % 8) << CHESS960_KINGSIDE_ROOK_FILE_SHIFT;
		} else {
			myChess960CastlingFiles &= ~(1 << CHESS960_KINGSIDE_ROOK_FILE_FLAG_SHIFT);
		}
	}

	@Override
	public int getChess960KingFile() {
		if (myVariant == Variant.STANDARD) {
			return Chess.NO_FILE;
		}
		if ((myChess960CastlingFiles & CHESS960_KING_FILE_FLAG_MASK) != 0) {
			return (myChess960CastlingFiles & CHESS960_KING_FILE_MASK) >>> CHESS960_KING_FILE_SHIFT;
		}
		return Chess.NO_FILE;
	}

	@Override
	public int getChess960QueensideRookFile() {
		if (myVariant == Variant.STANDARD) {
			return Chess.NO_FILE;
		}
		if ((myChess960CastlingFiles & CHESS960_QUEENSIDE_ROOK_FILE_FLAG_MASK) != 0) {
			return (myChess960CastlingFiles & CHESS960_QUEENSIDE_ROOK_FILE_MASK) >>> CHESS960_QUEENSIDE_ROOK_FILE_SHIFT;
		}
		return Chess.NO_FILE;
	}

	@Override
	public int getChess960KingsideRookFile() {
		if (myVariant == Variant.STANDARD) {
			return Chess.NO_FILE;
		}
		if ((myChess960CastlingFiles & CHESS960_KINGSIDE_ROOK_FILE_FLAG_MASK) != 0) {
			return (myChess960CastlingFiles & CHESS960_KINGSIDE_ROOK_FILE_MASK) >>> CHESS960_KINGSIDE_ROOK_FILE_SHIFT;
		}
		return Chess.NO_FILE;
	}

	@Override
	public short getMove(int from, int to, int promoPiece) {
		if (myVariant != Variant.CHESS960) {
			return super.getMove(from, to, promoPiece);
		} else {
			// as super.getMove: no validation!
			if (getColor(from) != getToPlay()) {
				return Move.ILLEGAL_MOVE;
			}
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

	@SuppressWarnings("unused")
	private static String internalCompare(PositionImpl pos1, PositionImpl pos2) {
		StringBuilder sb = new StringBuilder("Internal compare:").append(System.lineSeparator());

		if (pos1.myBbWhites != pos2.myBbWhites) {
			sb.append("mybbWhites differ").append(System.lineSeparator());
		}
		if (pos1.myBbBlacks != pos2.myBbBlacks) {
			sb.append("mybbBlacks differ").append(System.lineSeparator());
		}
		if (pos1.myBbPawns != pos2.myBbPawns) {
			sb.append("mybbPawns differ").append(System.lineSeparator());
		}
		if (pos1.myBbKnights != pos2.myBbKnights) {
			sb.append("mybbKnights differ").append(System.lineSeparator());
		}
		if (pos1.myBbBishops != pos2.myBbBishops) {
			sb.append("mybbBishops differ").append(System.lineSeparator());
		}
		if (pos1.myBbRooks != pos2.myBbRooks) {
			sb.append("mybbRooks differ").append(System.lineSeparator());
		}
		if (pos1.myWhiteKing != pos2.myWhiteKing) {
			sb.append("myWhiteKing differ").append(System.lineSeparator());
		}
		if (pos1.myBlackKing != pos2.myBlackKing) {
			sb.append("myBlackKing differ").append(System.lineSeparator());
		}
		if (pos1.myFlags != pos2.myFlags) {
			sb.append("myFlags differ").append(System.lineSeparator());
		}
		if (pos1.myHashCode != pos2.myHashCode) {
			sb.append("myHashCode differ").append(System.lineSeparator());
		}
		// array:
		if (pos1.myBakStack.length != pos2.myBakStack.length) {
			sb.append("myBakStack lengths differ").append(System.lineSeparator());
			for (int i = 0; i < Math.min(pos1.myBakStack.length, pos2.myBakStack.length); ++i) {
				if (pos1.myBakStack[i] != pos2.myBakStack[i]) {
					sb.append("myBakStack[").append(i).append("] differ: ").append(pos1.myBakStack[i]).append(" vs ")
							.append(pos2.myBakStack[i]).append(System.lineSeparator());
				}
			}
		} else {
			for (int i = 0; i < pos1.myBakStack.length; ++i) {
				if (pos1.myBakStack[i] != pos2.myBakStack[i]) {
					sb.append("myBakStack[").append(i).append("] differ: ").append(pos1.myBakStack[i]).append(" vs ")
							.append(pos2.myBakStack[i]).append(System.lineSeparator());
				}
			}
		}
		if (pos1.myBakIndex != pos2.myBakIndex) {
			sb.append("myBakIndex differ").append(System.lineSeparator());
		}
		// array:
		if (pos1.myMoveStack.length != pos2.myMoveStack.length) {
			sb.append("myMoveStack lengths differ").append(System.lineSeparator());
			for (int i = 0; i < Math.min(pos1.myMoveStack.length, pos2.myMoveStack.length); ++i) {
				if (pos1.myMoveStack[i] != pos2.myMoveStack[i]) {
					sb.append("myMoveStack[").append(i).append("] differ: ").append(Move.getString(pos1.myMoveStack[i]))
							.append(" vs ").append(Move.getString(pos2.myMoveStack[i])).append(System.lineSeparator());
				}
			}
		} else {
			for (int i = 0; i < pos1.myMoveStack.length; ++i) {
				if (pos1.myMoveStack[i] != pos2.myMoveStack[i]) {
					sb.append("myMoveStack[").append(i).append("] differ: ").append(Move.getString(pos1.myMoveStack[i]))
							.append(" vs ").append(Move.getString(pos2.myMoveStack[i])).append(System.lineSeparator());
				}
			}
		}
		if (pos1.myMoveStackIndex != pos2.myMoveStackIndex) {
			sb.append("myMoveStackIndex differ").append(System.lineSeparator());
		}
		// array:
		if (pos1.allMoves.length != pos2.allMoves.length) {
			sb.append("myMoves lengths differ").append(System.lineSeparator());
			for (int i = 0; i < Math.min(pos1.allMoves.length, pos2.allMoves.length); ++i) {
				if (pos1.allMoves[i] != pos2.allMoves[i]) {
					sb.append("allMoves[").append(i).append("] differ: ").append(Move.getString(pos1.allMoves[i])).append(" vs ")
							.append(Move.getString(pos2.allMoves[i])).append(System.lineSeparator());
				}
			}
		} else {
			for (int i = 0; i < pos1.allMoves.length; ++i) {
				if (pos1.allMoves[i] != pos2.allMoves[i]) {
					sb.append("allMoves[").append(i).append("] differ: ").append(Move.getString(pos1.allMoves[i])).append(" vs ")
							.append(Move.getString(pos2.allMoves[i])).append(System.lineSeparator());
				}
			}
		}
		if (pos1.myVariant != pos2.myVariant) {
			sb.append("myVariant differ").append(System.lineSeparator());
		}
		if (pos1.getChess960KingFile() != pos2.getChess960KingFile()) {
			sb.append("the chess960 king files differ").append(System.lineSeparator());
		}
		if (pos1.getChess960QueensideRookFile() != pos2.getChess960QueensideRookFile()) {
			sb.append("the chess960 queenside rook files differ").append(System.lineSeparator());
		}
		if (pos1.getChess960KingsideRookFile() != pos2.getChess960KingsideRookFile()) {
			sb.append("the chess960 kingside rook files differ").append(System.lineSeparator());
		}
		return sb.toString();
	}

	@Override
	public long getAttackersBB(int sqi, int color) {
		return getAllAttackers(sqi, color, false);
	}

	/*
	 * This class is made only for tests!
	 */
	public class PosInternalState {
		private final int bakIndex;
		private final List<Long> bakStack = new ArrayList<>();
		private final long bbWhites;
		private final long bbBlacks;
		private final long bbPawns;
		private final long bbKnights;
		private final long bbBishops;
		private final long bbRooks;
		private final int moveStackIndex;
		private final List<Short> moveStack = new ArrayList<>();

		public PosInternalState() {
			bakIndex = myBakIndex;
			for (long l : myBakStack) {
				if (l == 0L) {
					break;
				} else {
					bakStack.add(l);
				}
			}
			bbWhites = myBbWhites;
			bbBlacks = myBbBlacks;
			bbPawns = myBbPawns;
			bbKnights = myBbKnights;
			bbBishops = myBbBishops;
			bbRooks = myBbRooks;
			moveStackIndex = myMoveStackIndex;
			for (short s : myMoveStack) {
				if (s == 0) {
					break;
				} else {
					moveStack.add(s);
				}
			}
		}

		@Override
		public boolean equals(Object other) {
			if (other instanceof PosInternalState otherState) {
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
							System.err.println(
									"   " + index + ": " + this.bakStack.get(index) + " / " + otherState.bakStack.get(index));
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
							System.err.println(
									"   " + index + ": " + this.moveStack.get(index) + " / " + otherState.moveStack.get(index));
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

	/*
	 * The following flag is set in getAllPawnMoves if and only if an en passant move is
	 * created. This is done in order to detect the en passant possibility in getEpFEN below.
	 */
	private boolean myEnPassantFlag;

	@Override
	public String getEpFEN() {
		String s = getFEN(4).trim();
		if (s.endsWith("-")) {
			return s;
		} else {
			long targets = 0L;
			int sqiEP = getSqiEP();
			int epPawnSqi = sqiEP + (getToPlay() == Chess.WHITE ? -Chess.NUM_OF_COLS : Chess.NUM_OF_COLS);
			if (getSqiEP() != Chess.NO_SQUARE) {
				targets |= ofSquare(epPawnSqi);
			}
			myEnPassantFlag = false;
			getAllPawnMoves(0, targets);
			if (myEnPassantFlag) {
				return s;
			} else {
				String[] parts = s.split(" +");
				StringBuilder sb = new StringBuilder();
				if (parts.length == 4) {
					for (int i = 0; i < 3; ++i) {
						sb.append(parts[i]).append(" ");
					}
					sb.append("-");
					return sb.toString();
				} else {
					throw new RuntimeException("PositionImpl::getEpFEN: internal error.");
				}
			}
		}
	}

	@Override
	public PosInternalState getInternalState() {
		return new PosInternalState();
	}
}

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
package chesspresso.move;

import java.io.Serial;
import java.io.Serializable;

import chesspresso.Chess;

/**
 * Abstraction of a chess move.<br>
 *
 * This class provides support for two ways to encode moves:
 * </ol>
 * <li>Based on <code>short</code>: optimized for speed and memory but cannot be
 * used to print a SAN (short annotation, see PGN spec). Contains the following
 * information: from square, to square, capturing, promotion piece.
 * <li>Based on this class containing full information of the move such that a
 * SAN (and LAN) of the move can be printed without further assistance. Contains
 * all information of the short move plus information whether to skip rank
 * and file of the from square, check and mate information, the moving piece and
 * whether is a white move.<br>
 * Internal representation is based on two shorts.
 * </ol>
 *
 * In order to create a full move out of a short move a position is needed.
 *
 * @author Bernhard Seybold
 * 
 */
public class Move implements Serializable {

	// ======================================================================

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * Returns the moves in a normalized order such that the same set of moves
	 * always yields the same order. Implementation is: short values ascending.
	 */
	public static void normalizeOrder(short[] moves) {
		java.util.Arrays.sort(moves);
	}

	// TN: these comments are misleading and do not match the implementation.
	// ((Better comments and an idea for a less confusing implementation are
	// noted in my own files.)
	// ======================================================================
	// move encoding (users of the class should abstract from implementation and
	// use accessors)
	//
	// i n t s h o r t
	// 2 1
	// 109876 5432109876543210
	//
	// 0000xxxxxxxxxxxx specials
	// cccmmm 0pppttttttffffff regular move
	// cccmmm 1pppttttttffffff capturing move
	// cccmmm 1110ttttttffffff ep move
	// 1111xxxxxxxxxxxx castles (TN: should it be 0111xxxxxxxxxxxx?)

	// mmm moving piece
	// ccc captured piece
	// ppp promotion piece 000 = specials, 111 = castles, 110 = ep, 101 - 001 promo
	// pieces + 1 (5)
	// tttttt to sqi
	// ffffff from sqi
	//
	// value 0 means NO_MOVE, allowing arrays of moves to be initialized with 0
	// (default)

	private final static int TYPE_MASK = 0x00008000;
	private final static int REGULAR_MOVE = 0x00000000;
	private final static int CAPTURING_MOVE = 0x00008000;

	private final static int SPECIAL_MASK = 0x00007000; // Used for promotion, ep, castling and special moves.
	private final static int CASTLE_MOVE = 0x00007000;
	private final static int CASTLE_MOVE_CHESS960 = 0x00006000;
	private final static int EP_MOVE = 0x00006000;
	private final static int PROMO_QUEEN = 0x00005000;
	private final static int PROMO_ROOK = 0x00004000;
	private final static int PROMO_BISHOP = 0x00003000;
	private final static int PROMO_KNIGHT = 0x00002000;
	private final static int NO_PROMO = 0x00001000;
	public final static int SPECIAL_MOVE = 0x00000000; // allow defining of own specials
	public final static int NUM_OF_SPECIAL_MOVES = 0x00001000;

	private final static int FROM_SHIFT = 0;
	private final static int TO_SHIFT = 6;

	// precalculated castles moves
	public static final short WHITE_SHORT_CASTLE = CASTLE_MOVE | Chess.E1 << FROM_SHIFT | Chess.G1 << TO_SHIFT,
			WHITE_LONG_CASTLE = CASTLE_MOVE | Chess.E1 << FROM_SHIFT | Chess.C1 << TO_SHIFT,
			BLACK_SHORT_CASTLE = CASTLE_MOVE | Chess.E8 << FROM_SHIFT | Chess.G8 << TO_SHIFT,
			BLACK_LONG_CASTLE = CASTLE_MOVE | Chess.E8 << FROM_SHIFT | Chess.C8 << TO_SHIFT;

	/*
	 * TN: representing a null move. This is tricky, because I encode it as an
	 * impossible castling move. The much more logical choice NULL_MOVE =
	 * SPECIAL_MOVE + 2; made problems with Move::isValid(Move) and excluding it
	 * there has unknown implications in Move::isSpecial(Move).
	 * 
	 * (The choice CASTLE_MOVE +1 would correspond to Ka1-b1.)
	 */
	public static final short NULL_MOVE = CASTLE_MOVE | Chess.A1 << FROM_SHIFT | Chess.H1 << TO_SHIFT;

	/**
	 * Represents "no move". Set to 0 to allow rapid initialization of arrays to no
	 * moves (arrays are initialized to 0 by Java).
	 */
	public static final short NO_MOVE = SPECIAL_MOVE; // 0

	/**
	 * Representing an illegal move.
	 */
	public static final short ILLEGAL_MOVE = SPECIAL_MOVE + 1; // 1

	/**
	 * The range <code>[OTHER_SPECIALS,OTHER_SPECIALS+NUM_OF_OTHER_SPECIALS[</code>
	 * is reserved for clients of Move to define their own special moves. This can
	 * be used to indicate special conditions when a move is expected. Moves of the
	 * range above do not collide with any other moves.
	 */
	public static final short OTHER_SPECIALS = SPECIAL_MOVE + 16; // first 16 special reserved for future use

	/**
	 * Number of special moves which can be defined.
	 */
	public static final short NUM_OF_OTHER_SPECIALS = NUM_OF_SPECIAL_MOVES - 16;

	private static final String SHORT_CASTLE_STRING = "O-O", // big letter o, not zero
			LONG_CASTLE_STRING = "O-O-O";

	private static final String NULL_MOVE_STRING = "--";

	private static final int[] S_PROMO = new int[Chess.MAX_PIECE + 1];

	static {
		for (int i = 0; i <= Chess.MAX_PIECE; i++) {
			S_PROMO[i] = NO_PROMO;
		}
		S_PROMO[Chess.KNIGHT] = PROMO_KNIGHT;
		S_PROMO[Chess.BISHOP] = PROMO_BISHOP;
		S_PROMO[Chess.ROOK] = PROMO_ROOK;
		S_PROMO[Chess.QUEEN] = PROMO_QUEEN;
	}

	// ======================================================================

	/**
	 * Manufacture a regular move.
	 *
	 * @param fromSqi   the from square
	 * @param toSqi     the to square
	 * @param capturing whether it is a capturing move
	 */
	public static short getRegularMove(int fromSqi, int toSqi, boolean capturing) {
		if (capturing) {
			return (short) (CAPTURING_MOVE | fromSqi << FROM_SHIFT | toSqi << TO_SHIFT | NO_PROMO);
		} else {
			return (short) (REGULAR_MOVE | fromSqi << FROM_SHIFT | toSqi << TO_SHIFT | NO_PROMO);
		}
	}

	/**
	 * Manufacture a pawn move.
	 *
	 * @param fromSqi        the from square
	 * @param toSqi          the to square
	 * @param capturing      whether it is a capturing move
	 * @param promotionPiece set to a piece if it is a promotion move, set to
	 *                       <code>No_PIECE</code> otherwise
	 */
	public static short getPawnMove(int fromSqi, int toSqi, boolean capturing, int promotionPiece) {
		if (capturing) {
			return (short) (CAPTURING_MOVE | fromSqi << FROM_SHIFT | toSqi << TO_SHIFT | S_PROMO[promotionPiece]);
		} else {
			return (short) (REGULAR_MOVE | fromSqi << FROM_SHIFT | toSqi << TO_SHIFT | S_PROMO[promotionPiece]);
		}
	}

	/**
	 * Manufacture an en passant move.
	 *
	 * @param fromSqi the from square
	 * @param toSqi   the to square
	 */
	public static short getEPMove(int fromSqi, int toSqi) {
		return (short) (CAPTURING_MOVE | fromSqi << FROM_SHIFT | toSqi << TO_SHIFT | EP_MOVE);
	}

	/**
	 * Manufacture a short castle move.
	 *
	 * @param toPlay for which color
	 */
	public static short getShortCastle(int toPlay) {
		return (toPlay == Chess.WHITE ? WHITE_SHORT_CASTLE : BLACK_SHORT_CASTLE);
	}

	/**
	 * Manufacture a long castle move.
	 *
	 * @param toPlay for which color
	 */
	public static short getLongCastle(int toPlay) {
		return (toPlay == Chess.WHITE ? WHITE_LONG_CASTLE : BLACK_LONG_CASTLE);
	}

	/**
	 * A-file corresponds to Chess.A1, b-file to Chess.B1, etc. See also
	 * Position::setChess960CastlingFiles(..) etc.
	 */
	public static short getChess960Castle(int toPlay, int kingFile, int rookFile) {
		short move;
		if (toPlay == Chess.WHITE) {
			move = (short) (CASTLE_MOVE_CHESS960 | (kingFile % 8) << FROM_SHIFT | (rookFile % 8) << TO_SHIFT);
		} else {
			move = (short) (CASTLE_MOVE_CHESS960 | ((kingFile % 8) + Chess.A8) << FROM_SHIFT
					| ((rookFile % 8) + Chess.A8) << TO_SHIFT);
		}
		return move;
	}

	/**
	 * Get a null move.
	 *
	 */
	public static short getNullMove() {
		return NULL_MOVE;
	}

	/*
	 * =============================================================================
	 */

	public static int getFromSqi(short move) {
		return (move >>> FROM_SHIFT) & 0x3F;
	}

	public static int getToSqi(short move) {
		return (move >>> TO_SHIFT) & 0x3F;
	}

	public static boolean isCapturing(short move) {
		return (move & TYPE_MASK) == CAPTURING_MOVE;
	}

	public static boolean isPromotion(short move) {
		int promo = move & SPECIAL_MASK;
		return promo == PROMO_QUEEN || promo == PROMO_ROOK || promo == PROMO_BISHOP || promo == PROMO_KNIGHT;
	}

	public static int getPromotionPiece(short move) {
		int promo = move & SPECIAL_MASK;
		for (int piece = 0; piece <= Chess.MAX_PIECE; piece++) {
			if (S_PROMO[piece] == promo) {
				return piece;
			}
		}
		return Chess.NO_PIECE;
	}

	public static boolean isEPMove(short move) {
		return (move & SPECIAL_MASK) == EP_MOVE && (move & TYPE_MASK) == CAPTURING_MOVE;
	}

	public static int getEpCapturedPawnSquare(short move) {
		if (isEPMove(move)) {
			return Chess.coorToSqi(Chess.sqiToCol(getToSqi(move)), Chess.sqiToRow(getFromSqi(move)));
		} else {
			return Chess.NO_SQUARE;
		}
	}

	public static boolean isCastle(short move) {
		return (move & SPECIAL_MASK) == CASTLE_MOVE && move != NULL_MOVE;
	}

	public static boolean isShortCastle(short move) {
		return move == WHITE_SHORT_CASTLE | move == BLACK_SHORT_CASTLE;
	}

	public static boolean isLongCastle(short move) {
		return move == WHITE_LONG_CASTLE | move == BLACK_LONG_CASTLE;
	}

	public static boolean isCastleChess960(short move) {
		return (move & TYPE_MASK) != CAPTURING_MOVE && (move & SPECIAL_MASK) == CASTLE_MOVE_CHESS960;
	}

	public static boolean isShortCastleChess960(short move) {
		return (move & TYPE_MASK) != CAPTURING_MOVE && (move & SPECIAL_MASK) == CASTLE_MOVE_CHESS960
				&& (getFromSqi(move) < getToSqi(move));
	}

	public static boolean isLongCastleChess960(short move) {
		return (move & TYPE_MASK) != CAPTURING_MOVE && (move & SPECIAL_MASK) == CASTLE_MOVE_CHESS960
				&& (getFromSqi(move) > getToSqi(move));
	}

	public static boolean isNullMove(short move) {
		return (move == NULL_MOVE);
	}

	public static boolean isSpecial(short move) {
		return (move & SPECIAL_MASK) == SPECIAL_MOVE;
	}

	public static boolean isValid(short move) {
		return (move & SPECIAL_MASK) != SPECIAL_MOVE;
	}

	/*
	 * =============================================================================
	 */

	public static String getBinaryString(short move) {
		StringBuilder sb = new StringBuilder();
		for (int i = 15; i >= 0; i--) {
			if ((move & (1 << i)) != 0) {
				sb.append("1");
			} else {
				sb.append("0");
			}
		}
		return sb.toString();
	}

	/**
	 * Returns a string representation of the move.
	 *
	 * @param move move
	 * @return the string representation, e.g. e2xf4
	 */
	public static String getString(short move) {
		if (move == NO_MOVE) {
			return "<no move>";
		} else if (move == ILLEGAL_MOVE) {
			return "<illegal move>";
		} else if (isSpecial(move)) {
			return "<special>";
		} else if (isShortCastle(move) || isShortCastleChess960(move)) {
			return SHORT_CASTLE_STRING;
		} else if (isLongCastle(move) || isLongCastleChess960(move)) {
			return LONG_CASTLE_STRING;
		} else if (isNullMove(move)) {
			return NULL_MOVE_STRING;
		} else {
			StringBuilder sb = new StringBuilder();
			sb.append(Chess.sqiToStr(getFromSqi(move)));
			sb.append(isCapturing(move) ? 'x' : '-');
			sb.append(Chess.sqiToStr(getToSqi(move)));
			if (isPromotion(move)) {
				sb.append(Chess.pieceToChar(getPromotionPiece(move)));
			}
			return sb.toString();
		}
	}

	/*
	 * =============================================================================
	 */

	private static final Move MOVE_ILLEGAL_MOVE = new Move(ILLEGAL_MOVE, Chess.NO_PIECE, Chess.NO_ROW, Chess.NO_COL, false, false,
			false);

	/**
	 * Pre-manufactured illegal move, always returns the same instance.
	 *
	 * @return an illegal move
	 */
	public static Move createIllegalMove() {
		return MOVE_ILLEGAL_MOVE;
	}

	/**
	 * Convenience method to create a castle move.
	 *
	 * @param move        a castling move to based upon, must be a castling move
	 * @param isCheck     whether the move gives a check
	 * @param isMate      whether the move sets mate
	 * @param whiteToMove whether it is a white move
	 * @return the castle move
	 */
	public static Move createCastle(short move, boolean isCheck, boolean isMate, boolean whiteToMove) {
		return new Move(move, Chess.KING, Chess.NO_COL, Chess.NO_ROW, isCheck, isMate, whiteToMove);
	}

	/*
	 * =============================================================================
	 */

	// encoding for additional information
	private static final int COL_FROM_MUL = 0x00000010;
	private static final int COL_FROM_MASK = 0x000000F0;
	private static final int ROW_FROM_MUL = 0x00000100;
	private static final int ROW_FROM_MASK = 0x00000F00;
	private static final int CHECK_MUL = 0x00002000;
	private static final int CHECK_MASK = 0x00002000;
	private static final int MATE_MUL = 0x00004000;
	private static final int MATE_MASK = 0x00004000;
	private static final int TOPLAY_MUL = 0x00008000;
	private static final int TOPLAY_MASK = 0x00008000;
	private static final int MOVING_MUL = 0x00010000;
	private static final int MOVING_MASK = 0x00070000;

	private final short move;
	private final int info;

	/*
	 * =============================================================================
	 */

	/**
	 * Creates a full move.
	 *
	 * @param move        the short move
	 * @param movingPiece the piece moving
	 * @param colFrom     file if should be taken for SAN, <code>NO_COL</code>
	 *                    otherwise
	 * @param rowFrom     rank if should be taken for SAN, <code>NO_ROW</code>
	 *                    otherwise
	 * @param isCheck     whether the move gives a check
	 * @param isMate      whether the move sets mate
	 * @param whiteToMove whether it is a white move
	 */
	public Move(short move, int movingPiece, int colFrom, int rowFrom, boolean isCheck, boolean isMate, boolean whiteToMove) {
		this.move = move;
		info = COL_FROM_MUL * (colFrom - Chess.NO_COL) + ROW_FROM_MUL * (rowFrom - Chess.NO_ROW) + (isCheck ? CHECK_MUL : 0)
				+ (isMate ? MATE_MUL : 0) + (whiteToMove ? TOPLAY_MUL : 0) + MOVING_MUL * movingPiece;
	}

	/*
	 * =============================================================================
	 */

	public short getShortMoveDesc() {
		return move;
	}

	public int getPromotionPiece() {
		return Move.getPromotionPiece(move);
	}

	public int getFromSqi() {
		return Move.getFromSqi(move);
	}

	public int getToSqi() {
		return Move.getToSqi(move);
	}

	public int getMovingPiece() {
		return (info & MOVING_MASK) / MOVING_MUL;
	}

	public int getColFrom() {
		return (info & COL_FROM_MASK) / COL_FROM_MUL + Chess.NO_COL;
	}

	public int getRowFrom() {
		return (info & ROW_FROM_MASK) / ROW_FROM_MUL + Chess.NO_ROW;
	}

	public boolean isCapturing() {
		return Move.isCapturing(move);
	}

	public boolean isPromotion() {
		return Move.isPromotion(move);
	}

	public boolean isCheck() {
		return (info & CHECK_MASK) != 0;
	}

	public boolean isMate() {
		return (info & MATE_MASK) != 0;
	}

	public boolean isCastle() {
		return Move.isCastle(move);
	}

	public boolean isShortCastle() {
		return Move.isShortCastle(move);
	}

	public boolean isLongCastle() {
		return Move.isLongCastle(move);
	}

	public boolean isCastleChess960() {
		return Move.isCastleChess960(move);
	}

	public boolean isShortCastleChess960() {
		return Move.isShortCastleChess960(move);
	}

	public boolean isLongCastleChess960() {
		return Move.isLongCastleChess960(move);
	}

	public boolean isEPMove() {
		return Move.isEPMove(move);
	}

	public boolean isNullMove() {
		return Move.isNullMove(move);
	}

	public boolean isValid() {
		return Move.isValid(move);
	}

	public boolean isWhiteMove() {
		return (info & TOPLAY_MASK) != 0;
	}

	/*
	 * =============================================================================
	 */

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + info;
		result = prime * result + move;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if ((obj == null) || (getClass() != obj.getClass())) {
			return false;
		}
		Move other = (Move) obj;
		if (info != other.info) {
			return false;
		}
		return move == other.move;
	}

	/**
	 * Returns the LAN (long annotation, see PGN spec) of the move, e.g. Ne2xf4+.
	 *
	 * @return the LAN representation
	 */
	public String getLAN() {
		if (!isValid()) {
			return "<illegal move>";
		} else {
			StringBuilder sb = new StringBuilder();
			if (isShortCastle() || isShortCastleChess960()) {
				sb.append(SHORT_CASTLE_STRING);
			} else if (isLongCastle() || isLongCastleChess960()) {
				sb.append(LONG_CASTLE_STRING);
			} else if (isNullMove()) {
				sb.append(NULL_MOVE_STRING);
			} else {
				int piece = getMovingPiece();
				if (piece == Chess.NO_PIECE) {
					System.out.println(
							"Move::getLAN: unexpected NO_PIECE for " + move + " " + info + " " + Integer.toBinaryString(info));
				}
				if (piece != Chess.PAWN) {
					sb.append(Chess.pieceToChar(piece));
				}
				sb.append(Chess.sqiToStr(getFromSqi()));
				sb.append(isCapturing() ? "x" : "-");
				sb.append(Chess.sqiToStr(getToSqi()));
				if (isPromotion()) {
					sb.append('=').append(Chess.pieceToChar(getPromotionPiece()));
				}
			}
			if (isMate()) {
				sb.append('#');
			} else if (isCheck()) {
				sb.append('+');
			}
			return sb.toString();
		}
	}

	/**
	 * Returns the SAN (short annotation, see PGN spec) of the move, e.g. Nxf4+.
	 *
	 * @return the SAN representation
	 */
	public String getSAN() {
		if (!isValid()) {
			return "<illegal move>";
		} else {
			StringBuilder sb = new StringBuilder();
			if (isShortCastle() || isShortCastleChess960()) {
				sb.append(SHORT_CASTLE_STRING);
			} else if (isLongCastle() || isLongCastleChess960()) {
				sb.append(LONG_CASTLE_STRING);
			} else if (isNullMove()) {
				sb.append(NULL_MOVE_STRING);
			} else {
				int piece = getMovingPiece();
				if (piece == Chess.NO_PIECE) {
					System.out.println(
							"Move::getSAN: unexpected NO_PIECE for " + move + " " + info + " " + Integer.toBinaryString(info));
				}
				if (piece != Chess.PAWN) {
					sb.append(Chess.pieceToChar(piece));
				}
				if (getColFrom() != Chess.NO_COL) {
					sb.append(Chess.colToChar(getColFrom()));
				}
				if (getRowFrom() != Chess.NO_ROW) {
					sb.append(Chess.rowToChar(getRowFrom()));
				}
				if (isCapturing()) {
					sb.append("x");
				}
				sb.append(Chess.sqiToStr(getToSqi()));
				if (isPromotion()) {
					sb.append('=').append(Chess.pieceToChar(getPromotionPiece()));
				}
			}
			if (isMate()) {
				sb.append('#');
			} else if (isCheck()) {
				sb.append('+');
			}
			return sb.toString();
		}
	}

	@Override
	public String toString() {
		return getSAN();
	}

}

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

import chesspresso.Variant;

/**
 *
 * @author Bernhard Seybold
 * 
 */
public interface ImmutablePosition {

	// ======================================================================
	// constants for castle mask

	int NO_CASTLES = 0, WHITE_LONG_CASTLE = 1, WHITE_SHORT_CASTLE = 2, BLACK_LONG_CASTLE = 4, BLACK_SHORT_CASTLE = 8,
			WHITE_CASTLE = WHITE_LONG_CASTLE + WHITE_SHORT_CASTLE, BLACK_CASTLE = BLACK_LONG_CASTLE + BLACK_SHORT_CASTLE,
			ALL_CASTLES = WHITE_CASTLE + BLACK_CASTLE;

	// validity
	enum Validity {
		IS_VALID("The position is valid."), NO_ONE_TO_PLAY("It is not known who has the move."),
		NEGATIVE_PLY_NUMBER("The number of plies is negative."), NEGATIVE_HALF_MOVE_CLOCK("The half-move clock is negative."),
		INVALID_HALF_MOVE_CLOCK("The half-move clock is invalid."),
		INVALID_EN_PASSANT_SQUARE("The en passant square is invalid."),
		INVALID_NUMBER_OF_KINGS("The number of kings is invalid."),
		WRONG_KING_ATTACKED("A king is attacked, but the opponent has the move."),
		PAWN_ON_BACK_RANK("There is a pawn on the back rank."),
		KING_ATTACKED_BY_AN_UNMOVED_PAWN("The king is attacked by an unmoved pawn.");

		private final String description;

		Validity(String desc) {
			description = desc;
		}

		@Override
		public String toString() {
			return description;
		}
	}

	// ======================================================================
	// read access

	/**
	 * Return the stone currently on the given square.
	 *
	 * @param sqi the square
	 * @return the stone of the given square
	 */
	int getStone(int sqi);

	/**
	 * Return the piece currently on the given square.
	 *
	 * @param sqi the square
	 * @return the piece of the given square
	 */
	int getPiece(int sqi);

	/**
	 * Return the current en passant square.
	 *
	 * @return the current en passant square, NO_SQUARE if none
	 */
	int getSqiEP();

	/**
	 * Return the still allowed castles as mask.
	 *
	 * @return the still allowed castles as mask.
	 */
	int getCastles();

	/**
	 * Return the player whose turn it is.
	 *
	 * @return the player whose turn it is
	 */
	int getToPlay();

	/**
	 * Return the current ply number.
	 *
	 * @return the current ply number, starting at play no. 0
	 */
	int getPlyNumber();

	/**
	 * Return the offset for the ply numbers. A standard game has offset 0; if the
	 * game is a fragment starting with White's 12th move, the offset is 22; if it
	 * starts with Black's 20th move, the offset is 39.
	 *
	 * @return the ply offset
	 */
	int getPlyOffset();

	/**
	 * Return the number of moves since the last capture and the last pawn move.
	 * This number is used for the 50-move rule.
	 *
	 * @return the number of moves since the last capture and the last pawn move
	 */
	int getHalfMoveClock();

	/**
	 * Return whether the current position is valid or a error code.
	 *
	 * @return whether the current position is valid or a error code
	 */
	Validity getValidity();

	// ======================================================================
	// FEN

	/**
	 * Return the FEN representation of the current position {@link FEN}
	 *
	 * @return the FEN representation of the current position
	 */
	String getFEN();

	/**
	 * Return the first parts of the FEN representation of the current position
	 * {@link FEN}
	 *
	 * @return the first parts of the FEN representation of the current position
	 */
	String getFEN(int numberOfParts);

	/**
	 * Return a 4-part FEN of the position with en passant set if and only if an 
	 * en passant move is possible.
	 *  
	 * @return the special FEN
	 */
	String getEpFEN();

	// ======================================================================

	/**
	 * Returns whether the represented position is the start position
	 *
	 * @return whether the represented position is the start position
	 */
	boolean isStartPosition();

	// ======================================================================
	// hash codes

	/**
	 * Returns a 64bit hash code of the current position. 64bit should be enough to
	 * distinguish positions with almost no collisions. IDEA: add reference to paper
	 *
	 * @return a 64bit hash code
	 */
	long getHashCode();

	/**
	 * Returns a 32bit hash code of the current position. 32 bit is not enough to
	 * distinguish positions reliably, use only if collisions are handled.
	 *
	 * @return a 32bit hash code
	 */
	@Override
	int hashCode();

	/**
	 * @return the square of the White's king
	 */
	int getWhitesKingSquare();

	/**
	 * @return the square of the Black's king
	 */
	int getBlacksKingSquare();

	/**
	 * @return a bitboard for all pawns
	 */
	long getAllPawnsBB();

	/**
	 * @return a bitboard for White's pawns
	 */
	long getWhitePawnsBB();

	/**
	 * @return a bitboard for Black's pawns
	 */
	long getBlackPawnsBB();

	/**
	 * @return whether there is a check in the position
	 */
	boolean isCheck();

	/**
	 * @return whether the position a mate position
	 */
	boolean isMate();

	/**
	 * @return whether the position a stalemate position
	 */
	boolean isStaleMate();

	/**
	 * @return whether the position is a draw because of insufficient material 
	 */
	boolean isInsufficientMaterial();

	/**
	 * @return the total number of kings, pieces and pawns 
	 */
	int getNumberOfPieces();

	/**
	 * @return the chess variant
	 */
	Variant getVariant();

	/**
	 * @return the chess960 king file
	 */
	int getChess960KingFile();

	/**
	 * @return the chess960 queenside rook file
	 */
	int getChess960QueensideRookFile();

	/**
	 * @return the chess960 kingside rook file
	 */
	int getChess960KingsideRookFile();

	// ======================================================================

	/**
	 * Validates the internal state. Used for debugging and testing.
	 *
	 * @throws IllegalPositionException if the internal state is illegal
	 */
	void internalValidate() throws IllegalPositionException;

}

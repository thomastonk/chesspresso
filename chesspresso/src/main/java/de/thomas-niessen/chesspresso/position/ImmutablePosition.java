/*******************************************************************************
 * Basic version: Copyright (C) 2003 Bernhard Seybold. All rights reserved.
 * All changes since then: Copyright (C) 2019 Thomas Niessen. All rights reserved.
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

/**
 *
 * @author Bernhard Seybold
 * @version $Revision: 1.1 $
 */
public interface ImmutablePosition {

    // ======================================================================
    // constants for castle mask

    public final int NO_CASTLES = 0, WHITE_LONG_CASTLE = 1, WHITE_SHORT_CASTLE = 2, BLACK_LONG_CASTLE = 4,
	    BLACK_SHORT_CASTLE = 8, WHITE_CASTLE = WHITE_LONG_CASTLE + WHITE_SHORT_CASTLE,
	    BLACK_CASTLE = BLACK_LONG_CASTLE + BLACK_SHORT_CASTLE, ALL_CASTLES = WHITE_CASTLE + BLACK_CASTLE;

    // validity
    public static enum Validity {
	IS_VALID, NO_ONE_TO_PLAY, NEGATIVE_PLY_NUMBER, NEGATIVE_HALF_MOVE_CLOCK, INVALID_HALF_MOVE_CLOCK,
	INVALID_EN_PASSANT_SQUARE, INVALID_NUMBER_OF_KINGS, WRONG_KING_ATTACKED
    }

    // ======================================================================
    // read access

    /**
     * Return the stone currently on the given square.
     *
     * @param sqi the square
     * @return the stone of the given square
     */
    public int getStone(int sqi);

    /**
     * Return the current en passant square.
     *
     * @return the current en passant square, NO_SQUARE if none
     */
    public int getSqiEP();

    /**
     * Return the still allowed castles as mask.
     *
     * @return the still allowed castles as mask.
     */
    public int getCastles();

    /**
     * Return the player whose turn it is.
     *
     * @return the player whose turn it is
     */
    public int getToPlay();

    /**
     * Return the current ply number.
     *
     * @return the current ply number, starting at play no. 0
     */
    public int getPlyNumber();

    /**
     * Return the first ply number.
     *
     * @return the first ply number
     */
    public int getFirstPlyNumber();

    /**
     * Return the number of moves since the last capture and the last pawn move.
     * This number is used for the 50-move rule.
     *
     * @return the number of moves since the last capture and the last pawn move
     */
    public int getHalfMoveClock();

    /**
     * Return whether the current position is valid or a error code.
     *
     * @return whether the current position is valid or a error code
     */
    public Validity isValid();

    // ======================================================================
    // FEN

    /**
     * Return the FEN representation of the current position {@link FEN}
     *
     * @return the FEN representation of the current position
     */
    public String getFEN();

    // ======================================================================

    /**
     * Returns whether the represented position is the start position
     *
     * @return whether the represented position is the start position
     */
    public boolean isStartPosition();

    // ======================================================================
    // hash codes

    /**
     * Returns a 64bit hash code of the current position. 64bit should be enough to
     * distinguish positions with almost no collisions. TODO: add reference to paper
     *
     * @return a 64bit hash code
     */
    public long getHashCode();

    /**
     * Returns a 32bit hash code of the current position. 32 bit is not enough to
     * distinguish positions reliably, use only if collisions are handled.
     *
     * @return a 32bit hash code
     */
    public int hashCode();

    // ======================================================================

    /**
     * Validates the internal state. Used for debugging and testing.
     *
     * @throws IllegalPositionException if the internal state is illegal
     */
    void internalValidate() throws IllegalPositionException;

}

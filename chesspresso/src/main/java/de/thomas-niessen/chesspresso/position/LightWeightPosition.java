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

import chesspresso.Chess;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;

/**
 * A light-weight implementation of the position interface.
 *
 * This class is optimized for simplicity of the underlying data structure, not
 * for access speed nor for memory footprint. Use this class if you do not care
 * about performance.
 *
 * @author Bernhard Seybold
 * 
 */
public class LightWeightPosition extends AbstractMutablePosition {
    private int[] m_stone;
    private int m_sqiEP;
    private int m_castles;
    private int m_toPlay;
    private int m_plyNumber;
    private int m_halfMoveClock;

    /*
     * =============================================================================
     */

    public LightWeightPosition() {
	m_stone = new int[Chess.NUM_OF_SQUARES];
	clear();
    }

    public LightWeightPosition(ImmutablePosition position) {
	this();
	setPosition(position);
    }

    /*
     * =============================================================================
     */

    @Override
    public int getStone(int sqi) {
	return m_stone[sqi];
    }

    @Override
    public int getToPlay() {
	return m_toPlay;
    }

    @Override
    public int getSqiEP() {
	return m_sqiEP;
    }

    @Override
    public int getCastles() {
	return m_castles;
    }

    @Override
    public int getPlyNumber() {
	return m_plyNumber;
    }

    @Override
    public int getHalfMoveClock() {
	return m_halfMoveClock;
    }

    /*
     * =============================================================================
     */

    @Override
    public void setStone(int sqi, int stone) {
	if (m_stone[sqi] != stone) {
	    m_stone[sqi] = stone;
	    fireSquareChanged(sqi);
	}
    }

    @Override
    public void setCastles(int castles) {
	if (m_castles != castles) {
	    m_castles = castles;
	    fireCastlesChanged();
	}
    }

    @Override
    public void setSqiEP(int sqiEP) {
	if (m_sqiEP != sqiEP) {
	    m_sqiEP = sqiEP;
	    fireSqiEPChanged();
	}
    }

    @Override
    public void setToPlay(int toPlay) {
	if (m_toPlay != toPlay) {
	    m_toPlay = toPlay;
	    fireToPlayChanged();
	}
    }

    @Override
    public void setPlyNumber(int plyNumber) {
	if (m_plyNumber != plyNumber) {
	    m_plyNumber = plyNumber;
	    firePlyNumberChanged();
	}
    }

    @Override
    public void setHalfMoveClock(int halfMoveClock) {
	if (m_halfMoveClock != halfMoveClock) {
	    m_halfMoveClock = halfMoveClock;
	    fireHalfMoveClockChanged();
	}
    }

    /*
     * =============================================================================
     */

    public void doMove(short move) throws IllegalMoveException {
	throw new IllegalMoveException("Moves not supported");
    }

    public boolean canUndoMove() {
	return false;
    }

    public boolean undoMove() {
	return false;
    }

    public short getLastShortMove() throws IllegalMoveException {
	throw new IllegalMoveException("Moves not supported");
    }

    public Move getLastMove() throws IllegalMoveException {
	throw new IllegalMoveException("Moves not supported");
    }

    public boolean canRedoMove() {
	return false;
    }

    public boolean redoMove() {
	return false;
    }

}

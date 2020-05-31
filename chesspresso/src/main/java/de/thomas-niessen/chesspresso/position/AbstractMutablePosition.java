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
import java.util.Collections;
import java.util.List;

import chesspresso.Chess;

/**
 *
 * @author Bernhard Seybold
 * 
 */
public abstract class AbstractMutablePosition extends AbstractPosition implements MutablePosition {
    private final List<PositionListener> m_listeners;
    private final List<PositionChangeListener> m_changeListeners;
    protected boolean m_notifyListeners; // ... to check whether or not to fire
    protected boolean m_notifyPositionChanged;

    protected int m_firstPly;

    /*
     * =========================================================================
     */

    protected AbstractMutablePosition() {
	m_listeners = new ArrayList<>();
	m_changeListeners = new ArrayList<>();
	m_notifyListeners = true;
	m_notifyPositionChanged = true;
    }

    /*
     * =========================================================================
     */

    public int getPiece(int sqi) {
	return Chess.stoneToPiece(getStone(sqi));
    }

    public int getColor(int sqi) {
	return Chess.stoneToColor(getStone(sqi));
    }

    public boolean isSquareEmpty(int sqi) {
	return getStone(sqi) == Chess.NO_STONE;
    }

    /*
     * =========================================================================
     */

    public void toggleToPlay() {
	setToPlay(Chess.otherPlayer(getToPlay()));
    }

    /*
     * =========================================================================
     */

    @Override
    public void clear() {
	boolean notify = m_notifyPositionChanged;
	m_notifyPositionChanged = false;

	for (int sqi = 0; sqi < Chess.NUM_OF_SQUARES; sqi++) {
	    setStone(sqi, Chess.NO_STONE);
	}
	setSqiEP(Chess.NO_SQUARE);
	setCastles(NO_CASTLES);
	setToPlay(Chess.WHITE);
	setPlyNumber(0);
	setHalfMoveClock(0);

	m_notifyPositionChanged = notify;
	firePositionChanged();
    }

    @Override
    public void setStart() {
	boolean notify = m_notifyPositionChanged;
	m_notifyPositionChanged = false;

	FEN.initFromFEN(this, FEN.START_POSITION);

	m_notifyPositionChanged = notify;
	firePositionChanged();
    }

    @Override
    public void setPosition(ImmutablePosition position) {
	boolean notify = m_notifyPositionChanged;
	m_notifyPositionChanged = false;

	for (int sqi = 0; sqi < Chess.NUM_OF_SQUARES; sqi++) {
	    setStone(sqi, position.getStone(sqi));
	}
	setCastles(position.getCastles());
	setSqiEP(position.getSqiEP());
	setToPlay(position.getToPlay());
	setPlyNumber(position.getPlyNumber());
	setHalfMoveClock(position.getHalfMoveClock());

	m_notifyPositionChanged = notify;
	firePositionChanged();
    }

    /*
     * =========================================================================
     */

    public final void moveAllUp() {
	boolean notify = m_notifyPositionChanged;
	m_notifyPositionChanged = false;

	for (int sqi = Chess.H7; sqi >= Chess.A1; --sqi) {
	    int stone = getStone(sqi);
	    if (stone == Chess.WHITE_KING || stone == Chess.BLACK_KING) {
		setStone(sqi, Chess.NO_STONE);
		// Necessary to get a valid position again!
	    }
	    setStone(sqi + 8, stone);
	}
	for (int sqi = Chess.A1; sqi <= Chess.H1; ++sqi) {
	    setStone(sqi, Chess.NO_STONE);
	}

	setCastles(NO_CASTLES);
	setSqiEP(Chess.NO_SQUARE);
	setHalfMoveClock(0);

	m_notifyPositionChanged = notify;
	firePositionChanged();
    }

    public final void moveAllDown() {
	boolean notify = m_notifyPositionChanged;
	m_notifyPositionChanged = false;

	for (int sqi = Chess.A2; sqi <= Chess.H8; ++sqi) {
	    int stone = getStone(sqi);
	    if (stone == Chess.WHITE_KING || stone == Chess.BLACK_KING) {
		setStone(sqi, Chess.NO_STONE);
		// Necessary to get a valid position again!
	    }
	    setStone(sqi - 8, stone);
	}
	for (int sqi = Chess.A8; sqi <= Chess.H8; ++sqi) {
	    setStone(sqi, Chess.NO_STONE);
	}

	setCastles(NO_CASTLES);
	setSqiEP(Chess.NO_SQUARE);
	setHalfMoveClock(0);

	m_notifyPositionChanged = notify;
	firePositionChanged();
    }

    public final void moveAllLeft() {
	boolean notify = m_notifyPositionChanged;
	m_notifyPositionChanged = false;

	for (int sqi = Chess.A1; sqi <= Chess.H8; ++sqi) {
	    if (sqi % 8 != 7) {
		int stone = getStone(sqi + 1);
		if (stone == Chess.WHITE_KING || stone == Chess.BLACK_KING) {
		    setStone(sqi + 1, Chess.NO_STONE);
		    // Necessary to get a valid position again!
		}
		setStone(sqi, stone);
	    } else {
		setStone(sqi, Chess.NO_STONE);
	    }
	}

	setCastles(NO_CASTLES);
	setSqiEP(Chess.NO_SQUARE);
	setHalfMoveClock(0);

	m_notifyPositionChanged = notify;
	firePositionChanged();
    }

    public final void moveAllRight() {
	boolean notify = m_notifyPositionChanged;
	m_notifyPositionChanged = false;

	for (int sqi = Chess.H8; sqi >= Chess.A1; --sqi) {
	    if (sqi % 8 != 0) {
		int stone = getStone(sqi - 1);
		if (stone == Chess.WHITE_KING || stone == Chess.BLACK_KING) {
		    setStone(sqi - 1, Chess.NO_STONE);
		    // Necessary to get a valid position again!
		}
		setStone(sqi, stone);
	    } else {
		setStone(sqi, Chess.NO_STONE);
	    }
	}

	setCastles(NO_CASTLES);
	setSqiEP(Chess.NO_SQUARE);
	setHalfMoveClock(0);

	m_notifyPositionChanged = notify;
	firePositionChanged();
    }

    /*
     * =========================================================================
     */

    public final void invert() {
	/*---------- invert stones ----------*/
	// avoid to have two kings of the same on the board at the same time
	int[] stones = new int[Chess.NUM_OF_SQUARES];
	for (int sqi = 0; sqi < Chess.NUM_OF_SQUARES; sqi++) {
	    stones[sqi] = getStone(sqi);
	    setStone(sqi, Chess.NO_STONE);
	}
	for (int sqi = 0; sqi < Chess.NUM_OF_SQUARES; sqi++) {
	    int partnerSqi = Chess.coorToSqi(Chess.sqiToCol(sqi), Chess.NUM_OF_ROWS - Chess.sqiToRow(sqi) - 1);
	    setStone(sqi, Chess.getOpponentStone(stones[partnerSqi]));
	}

	/*---------- invert en passant square ----------*/
	int sqiEP = getSqiEP();
	if (sqiEP != Chess.NO_SQUARE) {
	    setSqiEP(Chess.coorToSqi(Chess.sqiToCol(sqiEP), Chess.NUM_OF_ROWS - Chess.sqiToRow(sqiEP) - 1));
	}

	/*---------- invert castles ----------*/
	int castles = getCastles();
	setCastles(NO_CASTLES);
	if ((castles & WHITE_SHORT_CASTLE) != 0)
	    includeCastles(BLACK_SHORT_CASTLE);
	if ((castles & WHITE_LONG_CASTLE) != 0)
	    includeCastles(BLACK_LONG_CASTLE);
	if ((castles & BLACK_SHORT_CASTLE) != 0)
	    includeCastles(WHITE_SHORT_CASTLE);
	if ((castles & BLACK_LONG_CASTLE) != 0)
	    includeCastles(WHITE_LONG_CASTLE);

	/*---------- invert to play ----------*/
	toggleToPlay();
    }

    /*
     * =========================================================================
     */
    // convenience methods

    public final void includeCastles(int whichCastles) {
	setCastles(getCastles() | whichCastles);
    }

    public final void excludeCastles(int whichCastles) {
	setCastles(getCastles() & (~whichCastles));
    }

    public final void resetHalfMoveClock() {
	setHalfMoveClock(0);
    }

    public final void incHalfMoveClock() {
	setHalfMoveClock(getHalfMoveClock() + 1);
    }

    /*
     * =========================================================================
     */
    // trigger listeners

    protected void fireSquareChanged(int sqi) {
	if (m_notifyListeners) {
	    int stone = getStone(sqi);
	    for (PositionListener listener : m_listeners) {
		listener.squareChanged(sqi, stone);
	    }
	    firePositionChanged();
	}
    }

    protected void fireToPlayChanged() {
	if (m_notifyListeners) {
	    int toPlay = getToPlay();
	    for (PositionListener listener : m_listeners) {
		listener.toPlayChanged(toPlay);
	    }
	    firePositionChanged();
	}
    }

    protected void fireSqiEPChanged() {
	if (m_notifyListeners) {
	    int sqiEP = getSqiEP();
	    for (PositionListener listener : m_listeners) {
		listener.sqiEPChanged(sqiEP);
	    }
	    firePositionChanged();
	}
    }

    protected void fireCastlesChanged() {
	if (m_notifyListeners) {
	    int castles = getCastles();
	    for (PositionListener listener : m_listeners) {
		listener.castlesChanged(castles);
	    }
	    firePositionChanged();
	}
    }

    protected void firePlyNumberChanged() {
	if (m_notifyListeners) {
	    int plyNumber = getPlyNumber();
	    for (PositionListener listener : m_listeners) {
		listener.plyNumberChanged(plyNumber);
	    }
	    firePositionChanged();
	}
    }

    protected void fireHalfMoveClockChanged() {
	if (m_notifyListeners) {
	    int halfMoveClock = getHalfMoveClock();
	    for (PositionListener listener : m_listeners) {
		listener.halfMoveClockChanged(halfMoveClock);
	    }
	    firePositionChanged();
	}
    }

    protected void fireMoveDone(short move) {
	if (m_notifyListeners) {
	    for (PositionChangeListener listener : m_changeListeners) {
		listener.notifyMoveDone(this, move);
	    }
	}
    }

    protected void fireMoveUndone() {
	if (m_notifyListeners) {
	    for (PositionChangeListener listener : m_changeListeners) {
		listener.notifyMoveUndone(this);
	    }
	}
    }

    // private void firePositionChanged()
    public void firePositionChanged() {
	if (m_notifyPositionChanged) {
	    for (PositionChangeListener listener : m_changeListeners) {
		listener.notifyPositionChanged(this);
	    }
	}
    }

    /*
     * =========================================================================
     */
    // IChPositionListener

    public final void addPositionListener(PositionListener listener) {
	if (!m_listeners.contains(listener)) {
	    m_listeners.add(listener);
	}
	for (int sqi = 0; sqi < Chess.NUM_OF_SQUARES; sqi++) {
	    listener.squareChanged(sqi, getStone(sqi));
	}
	listener.toPlayChanged(getToPlay());
	listener.castlesChanged(getCastles());
	listener.sqiEPChanged(getSqiEP());
    }

    public final void removePositionListener(PositionListener listener) {
	m_listeners.remove(listener);
    }

    public final synchronized void setNotifyListeners(boolean notify) {
	m_notifyListeners = notify;
    }

    /*
     * =========================================================================
     */
    // PositionChangeListener

    public final void addPositionChangeListener(PositionChangeListener listener) {
	if (!m_changeListeners.contains(listener)) {
	    m_changeListeners.add(listener);
	}

	listener.notifyPositionChanged(this); // for initialization
    }

    public final void removePositionChangeListener(PositionChangeListener listener) {
	m_changeListeners.remove(listener);
    }

    public final List<PositionChangeListener> getPositionChangeListeners() {
	return Collections.unmodifiableList(m_changeListeners);
    }

    @Override
    public void setFirstPlyNumber(int plyNumber) {
	m_firstPly = plyNumber;
    }

    @Override
    public int getFirstPlyNumber() {
	return m_firstPly;
    }
}

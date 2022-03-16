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
import chesspresso.Variant;
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
	private final int[] m_stone;
	private int m_sqiEP;
	private int m_castles;
	private int m_toPlay;
	private int m_plyNumber;
	private int m_halfMoveClock;

	private final List<PositionListener> listeners = new ArrayList<>();

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
	public void initFromFEN(String fen, boolean validate) throws InvalidFenException {
		FEN.initFromFEN(this, fen, validate);
		firePositionChanged();
	}

	/*
	 * =============================================================================
	 */

	@Override
	public void setStone(int sqi, int stone) {
		if (m_stone[sqi] != stone) {
			m_stone[sqi] = stone;
			firePositionChanged();
		}
	}

	@Override
	public void setCastles(int castles) {
		if (m_castles != castles) {
			m_castles = castles;
			firePositionChanged();
		}
	}

	@Override
	public void setSqiEP(int sqiEP) {
		if (m_sqiEP != sqiEP) {
			m_sqiEP = sqiEP;
			firePositionChanged();
		}
	}

	@Override
	public void setToPlay(int toPlay) {
		if (m_toPlay != toPlay) {
			m_toPlay = toPlay;
			firePositionChanged();
		}
	}

	@Override
	public void setHalfMoveClock(int halfMoveClock) {
		if (m_halfMoveClock != halfMoveClock) {
			m_halfMoveClock = halfMoveClock;
			firePositionChanged();
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

	@Override
	public void setVariant(Variant variant) {
		throw new UnsupportedOperationException("LightweightPosition::setVariant: method has not been implemented yet.");
	}

	@Override
	public void setChess960CastlingFiles(int kingFile, int queensideRookFile, int kingsideRookFile) {
		throw new UnsupportedOperationException("LightweightPosition::getWhitesKingSquare: method has not been implemented yet.");
	}

	@Override
	public int getWhitesKingSquare() {
		throw new UnsupportedOperationException("LightweightPosition::getWhitesKingSquare: method has not been implemented yet.");
	}

	@Override
	public int getBlacksKingSquare() {
		throw new UnsupportedOperationException("LightweightPosition::getBlacksKingSquare: method has not been implemented yet.");
	}

	@Override
	public long getAllPawnsBB() {
		throw new UnsupportedOperationException("LightweightPosition::getAllPawnsBB: method has not been implemented yet.");
	}

	@Override
	public long getWhitePawnsBB() {
		throw new UnsupportedOperationException("LightweightPosition::getWhitePawnsBB: method has not been implemented yet.");
	}

	@Override
	public long getBlackPawnsBB() {
		throw new UnsupportedOperationException("LightweightPosition::getBlackPawnsBB: method has not been implemented yet.");
	}

	@Override
	public boolean isCheck() {
		throw new UnsupportedOperationException("LightweightPosition::isCheck: method has not been implemented yet.");
	}

	@Override
	public boolean isMate() {
		throw new UnsupportedOperationException("LightweightPosition::isMate: method has not been implemented yet.");
	}

	@Override
	public boolean isStaleMate() {
		throw new UnsupportedOperationException("LightweightPosition::isStaleMate: method has not been implemented yet.");
	}

	@Override
	public boolean isInsufficientMaterial() {
		throw new UnsupportedOperationException(
				"LightweightPosition::isInsufficientMaterial: method has not been implemented yet.");
	}

	@Override
	public int getNumberOfPieces() {
		throw new UnsupportedOperationException("LightweightPosition::getNumberOfPieces: method has not been implemented yet.");
	}

	@Override
	public Variant getVariant() {
		throw new UnsupportedOperationException("LightweightPosition::getVariant: method has not been implemented yet.");
	}

	@Override
	public int getChess960KingFile() {
		throw new UnsupportedOperationException("LightweightPosition::getChess960KingFile: method has not been implemented yet.");
	}

	@Override
	public int getChess960QueensideRookFile() {
		throw new UnsupportedOperationException(
				"LightweightPosition::getChess960QueensideRookFile: method has not been implemented yet.");
	}

	@Override
	public int getChess960KingsideRookFile() {
		throw new UnsupportedOperationException(
				"LightweightPosition::getChess960KingsideRookFile: method has not been implemented yet.");
	}

	@Override
	public String getEpFEN() {
		throw new UnsupportedOperationException("LightweightPosition::getEpFEN: method has not been implemented yet.");
	}

	// everything for PositionListeners

	public final void addPositionListener(PositionListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
		// for initialization
		listener.positionChanged(this);
	}

	public final void removePositionListener(PositionListener listener) {
		listeners.remove(listener);
	}

	public final synchronized List<PositionListener> getPositionListeners() {
		return Collections.unmodifiableList(listeners);
	}

	private void firePositionChanged() {
		for (PositionListener listener : listeners) {
			listener.positionChanged(this);
		}
	}

}

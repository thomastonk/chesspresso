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

/**
 * @author Bernhard Seybold
 */
public abstract class AbstractMutablePosition extends AbstractPosition implements MutablePosition {

	protected int m_plyOffset;

	/*
	 * =========================================================================
	 */

	protected AbstractMutablePosition() {
	}

	/*
	 * =========================================================================
	 */

	@Override
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

	@Override
	public void toggleToPlay() {
		setToPlay(Chess.otherPlayer(getToPlay()));
	}

	/*
	 * =========================================================================
	 */

	@Override
	public void clear() {
		for (int sqi = 0; sqi < Chess.NUM_OF_SQUARES; sqi++) {
			setStone(sqi, Chess.NO_STONE);
		}
		setSqiEP(Chess.NO_SQUARE);
		setCastles(NO_CASTLES);
		setToPlay(Chess.WHITE);
		setPlyNumber(0);
		setHalfMoveClock(0);
	}

	@Override
	public void setStart() {
		initFromFEN(FEN.START_POSITION, true);
	}

	@Override
	public void setPosition(ImmutablePosition position) {
		for (int sqi = 0; sqi < Chess.NUM_OF_SQUARES; sqi++) {
			setStone(sqi, position.getStone(sqi));
		}
		setCastles(position.getCastles());
		setSqiEP(position.getSqiEP());
		setToPlay(position.getToPlay());
		setPlyNumber(position.getPlyNumber());
		setHalfMoveClock(position.getHalfMoveClock());
	}

	/*
	 * =========================================================================
	 */

	@Override
	public final void moveAllUp() {
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
	}

	@Override
	public final void moveAllDown() {
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
	}

	@Override
	public final void moveAllLeft() {
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
	}

	@Override
	public final void moveAllRight() {
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
	}

	/*
	 * =========================================================================
	 */
	@Override
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

	@Override
	public void setPlyOffset(int plyOffset) {
		m_plyOffset = plyOffset;
	}

	@Override
	public int getPlyOffset() {
		return m_plyOffset;
	}
}

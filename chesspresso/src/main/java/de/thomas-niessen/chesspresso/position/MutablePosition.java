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

import java.util.Collection;

/**
 * @author $Author: Bernhard Seybold$
 */
public interface MutablePosition extends ImmutablePosition {
	void clear();

	void initFromFEN(String fen, boolean validate) throws InvalidFenException;

	/** This method takes the current state of the argument as a snapshot and
	 * copies this snapshot into this. It is not any kind of deep copying into
	 * this. */
	void setPositionSnapshot(ImmutablePosition position);

	void setStart();

	void setStone(int sqi, int stone);

	void setCastles(int castles);

	void setSqiEP(int sqiEP);

	void setToPlay(int toPlay);

	void toggleToPlay();

	void setPlyOffset(int plyOffset);

	void setHalfMoveClock(int halfMoveClock);

	void setChess960();

	void setChess960CastlingFiles(int kingFile, int queensideRookFile, int kingsideRookFile);

	void moveAllUp();

	void moveAllDown();

	void moveAllLeft();

	void moveAllRight();

	void rotate90DegreesClockwise();

	void moveUp(Collection<Integer> squares);

	void moveDown(Collection<Integer> squares);

	void moveLeft(Collection<Integer> squares);

	void moveRight(Collection<Integer> squares);

	void rotate90DegreesClockwise(Collection<Integer> squares);

	void flipAboutA1H8Diagonal();

	void flipAboutA8H1Diagonal();

	void flipAboutA1H8Diagonal(Collection<Integer> squares);

	void flipAboutA8H1Diagonal(Collection<Integer> squares);

	void invert();
}

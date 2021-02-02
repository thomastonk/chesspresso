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

import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;

/**
 *
 * @author $Author: Bernhard Seybold $
 * 
 */
public interface MoveablePosition extends MutablePosition {
	void doMove(short move) throws IllegalMoveException;

	void doMove(Move move) throws IllegalMoveException;

	short getLastShortMove() throws IllegalMoveException;

	Move getLastMove() throws IllegalMoveException;

	boolean canUndoMove();

	boolean undoMove();

	boolean canRedoMove();

	boolean redoMove();

	short getMove(int from, int to, int promoPiece);

	short[] getAllMoves();

	short getPawnMove(int colFrom, int to, int promoPiece);

	short getPieceMove(int piece, int colFrom, int rowFrom, int to);
}

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
 *
 * @author Bernhard Seybold
 */
public abstract class AbstractMoveablePosition extends AbstractMutablePosition implements MoveablePosition {

	@Override
	public void doMove(Move move) throws IllegalMoveException {
		doMove(move.getShortMoveDesc());
	}

	@Override
	public short getMove(int from, int to, int promoPiece) {
		if (getColor(from) != getToPlay())
			return Move.ILLEGAL_MOVE; // =====>
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
		} else if (piece == Chess.KING && (to - from) == 2) {
			return Move.getShortCastle(getToPlay());
		} else if (piece == Chess.KING && (to - from) == -2) {
			return Move.getLongCastle(getToPlay());
		} else {
			return Move.getRegularMove(from, to, !isSquareEmpty(to));
		}
	}

}

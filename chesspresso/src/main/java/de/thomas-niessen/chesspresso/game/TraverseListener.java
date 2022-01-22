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
package chesspresso.game;

import chesspresso.move.Move;

/**
 * Listener for traverse games (see Game::traverse).
 *
 * @author Bernhard Seybold, Thomas Niessen
 */
public interface TraverseListener {

	/* This method is called just after the traversed game is set to its start position. */
	default void initTraversal() {
	}

	/* The traversed game's position will be the one after the move in the parameters. */
	void notifyMove(Move move, short[] nags, String preMoveComment, String postMoveComment, int plyNumber, int level);

	/* This method is called before the first move of a new sub-line is executed. No call for the mainline. */
	void notifyLineStart(int level);

	/* This method is called after the last move of a sub-line is executed. No call for the mainline. */
	void notifyLineEnd(int level);
}

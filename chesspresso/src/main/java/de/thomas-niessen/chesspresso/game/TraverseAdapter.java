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
 * Default implementation of a TraverseListener.
 * 
 *  @author Thomas Niessen
 */
public class TraverseAdapter implements TraverseListener {

	@Override
	public void notifyMove(Move move, short[] nags, String preMoveComment, String postMoveComment, int plyNumber, int level,
			String fenBeforeMove) {
	}

	@Override
	public void notifyLineStart(int level) {
	}

	@Override
	public void notifyLineEnd(int level) {
	}
}

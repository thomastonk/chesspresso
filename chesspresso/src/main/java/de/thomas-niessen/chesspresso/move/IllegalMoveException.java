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
package chesspresso.move;

/**
 * Exception indicating an illegal move.
 *
 * @author Bernhard Seybold
 * 
 */
@SuppressWarnings("serial")
public class IllegalMoveException extends Exception {
    public IllegalMoveException(short move) {
	super("Illegal move: " + Move.getString(move));
    }

    public IllegalMoveException(short move, String msg) {
	super("Illegal move: " + Move.getString(move) + ": " + msg);
    }

    public IllegalMoveException(Move move) {
	super("Illegal move: " + move);
    }

    public IllegalMoveException(Move move, String msg) {
	super("Illegal move: " + move + ": " + msg);
    }

    public IllegalMoveException(String msg) {
//		super("Illegal move: " + msg);
	super(msg);
    }
}

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

/**
 *
 * @author $Author: Bernhard Seybold $
 * 
 */
public interface MutablePosition extends ImmutablePosition {
    void clear();

    void setPosition(ImmutablePosition position);

    void setStart();

    void setStone(int sqi, int stone);

    void setCastles(int castles);

    void setSqiEP(int sqiEP);

    void setToPlay(int toPlay);

    void setPlyNumber(int plyNumber);

    void setFirstPlyNumber(int plyNumber);

    void setHalfMoveClock(int halfMoveClock);
}

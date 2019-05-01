/*******************************************************************************
 * Basic version: Copyright (C) 2003 Bernhard Seybold. All rights reserved.
 * All changes since then: Copyright (C) 2019 Thomas Niessen. All rights reserved.
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
 * @author $Author: BerniMan $
 * @version $Revision: 1.1 $
 */
public interface MutablePosition extends ImmutablePosition {
	public void clear();

	public void set(ImmutablePosition position);

	public void setStart();

	public void setStone(int sqi, int stone);

	public void setCastles(int castles);

	public void setSqiEP(int sqiEP);

	public void setToPlay(int toPlay);

	public void setPlyNumber(int plyNumber);

	public void setFirstPlyNumber(int plyNumber);

	public void setHalfMoveClock(int halfMoveClock);
}

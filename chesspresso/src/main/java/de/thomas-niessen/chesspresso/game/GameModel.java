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
package chesspresso.game;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

/**
 *
 * @author Bernhard Seybold
 * @version $Revision: 1.1 $
 */
public class GameModel implements Serializable {
	private static final long serialVersionUID = 1L;

	private GameHeaderModel m_headerModel;
	private GameMoveModel m_moveModel;

	/* ================================================================================ */

	public GameModel() {
		m_headerModel = new GameHeaderModel();
		m_moveModel = new GameMoveModel();
	}

	public GameModel(GameHeaderModel headerModel, GameMoveModel moveModel) {
		m_headerModel = headerModel;
		m_moveModel = moveModel;
	}

	public GameModel(DataInput in, int headerMode, int movesMode) throws IOException {
		load(in, headerMode, movesMode);
	}

	// TN added:
	public GameModel getDeepCopy() {
		return new GameModel(m_headerModel.getDeepCopy(), m_moveModel.getDeepCopy());
	}

	/* ================================================================================ */

	public GameHeaderModel getHeaderModel() {
		return m_headerModel;
	}

	public GameMoveModel getMoveModel() {
		return m_moveModel;
	}

	/* ================================================================================ */

	public void load(DataInput in, int headerMode, int movesMode) throws IOException {
		m_headerModel = new GameHeaderModel(in, headerMode);
		m_moveModel = new GameMoveModel(in, movesMode);
	}

	public void save(DataOutput out, int headerMode, int movesMode) throws IOException {
		m_headerModel.save(out, headerMode);
		m_moveModel.save(out, movesMode);
	}

	/* ================================================================================ */

	public int hashCode() {
		return getMoveModel().hashCode();
	}

	public boolean equals(Object obj) {
		if (obj == this)
			return true; // =====>
		if (!(obj instanceof GameModel))
			return false; // =====>
		GameModel gameModel = (GameModel) obj;
		return gameModel.getMoveModel().equals(getMoveModel());
	}

	/* ================================================================================ */

	public String toString() {
		return m_headerModel.toString();
	}
}

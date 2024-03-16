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

import java.io.Serial;
import java.io.Serializable;

/**
 * @author Bernhard Seybold
 */
final class GameModel implements Comparable<GameModel>, Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private final GameHeaderModel headerModel;
	private final GameMoveModel moveModel;

	// =============================================================================

	GameModel() {
		headerModel = new GameHeaderModel();
		moveModel = new GameMoveModel();
	}

	GameModel(GameHeaderModel headerModel, GameMoveModel moveModel) {
		this.headerModel = headerModel;
		this.moveModel = moveModel;
	}

	public GameModel getDeepCopy() {
		return new GameModel(headerModel.getDeepCopy(), moveModel.getDeepCopy());
	}

	// =============================================================================

	GameHeaderModel getHeaderModel() {
		return headerModel;
	}

	GameMoveModel getMoveModel() {
		return moveModel;
	}

	// =============================================================================
	// hashCode, equals and compareTo all depend on the move model's methods.

	@Override
	public int hashCode() {
		return getMoveModel().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof GameModel gameModel)) {
			return false;
		}
		return gameModel.getMoveModel().equals(getMoveModel());
	}

	@Override
	public int compareTo(GameModel other) {
		return getMoveModel().compareTo(other.getMoveModel());
	}

	// =============================================================================

	@Override
	public String toString() {
		return headerModel.toString();
	}

}

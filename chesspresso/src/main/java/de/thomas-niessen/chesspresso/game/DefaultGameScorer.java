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

/**
 * Implementation of a games scorer. The score is higher the more information is filled into the game header.
 *
 * <ul>
 * <li>white, black, event, site: 1 point for each letter
 * <li>date, result; 8 points if exist
 * <li>white elo, black elo: 4 points if exist
 * <li>eco: 1 point if exists
 * </ul>
 *
 * @author Bernhard Seybold
 * @version $Revision: 1.1 $
 */
public class DefaultGameScorer implements GameScorer {

	public int getScore(GameModel gameModel) {
		String s;
		GameHeaderModel headerModel = gameModel.getHeaderModel();
		GameMoveModel moveModel = gameModel.getMoveModel();

		int score = 0;
		score += moveModel.getTotalNumOfPlies() * 3;
		score += moveModel.getTotalCommentSize() * 1;

		s = headerModel.getWhite();
		if (s != null)
			score += s.length() * 1;
		s = headerModel.getBlack();
		if (s != null)
			score += s.length() * 1;
		if (headerModel.getDate() != null)
			score += 8;
		if (headerModel.getResultStr() != null)
			score += 8; // TODO "real" result only
		s = headerModel.getEvent();
		if (s != null)
			score += s.length() * 1;
		s = headerModel.getSite();
		if (s != null)
			score += s.length() * 1;
		if (headerModel.getWhiteEloStr() != null)
			score += 4;
		if (headerModel.getBlackEloStr() != null)
			score += 4;
		if (headerModel.getECO() != null)
			score += 1;

		return score;
	}

}

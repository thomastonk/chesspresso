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
package chesspresso.game.view;

import java.awt.Component;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import chesspresso.game.Game;
import chesspresso.pgn.PGN;
import chesspresso.position.NAG;

/**
 * @author Thomas Niessen
 */
@SuppressWarnings("serial")
public class TextViewerPopup extends JPopupMenu {

	private final Game game;
	private final GameTextViewer textViewer;
	private final Component parent;

	public TextViewerPopup(Game game, GameTextViewer textViewer, Component parent) {
		this.game = game;
		this.textViewer = textViewer;
		this.parent = parent;
		init();
	}

	private void init() {
		{
			JMenu punctuationNagMenu = new JMenu("!, ?, ...");
			String[] nags = NAG.getDefinedShortNags(NAG.PUNCTUATION_NAG_BEGIN, NAG.PUNCTUATION_NAG_END);
			for (String nag : nags) {
				JMenuItem item = new JMenuItem(ActionFactory.getNagAction(game, nag));
				punctuationNagMenu.add(item);
			}
			JMenuItem noneItem = new JMenuItem(ActionFactory.getRemovePunctuationAction(game));
			punctuationNagMenu.add(noneItem);
			add(punctuationNagMenu);
		}
		{
			JMenu evaluationNagMenu = new JMenu("+-, =, ...");
			String[] nags = NAG.getDefinedShortNags(NAG.EVALUATION_NAG_BEGIN, NAG.EVALUATION_NAG_END);
			for (String nag : nags) {
				JMenuItem item = new JMenuItem(ActionFactory.getNagAction(game, nag));
				evaluationNagMenu.add(item);
			}
			JMenuItem noneItem = new JMenuItem(ActionFactory.getRemoveEvaluationAction(game));
			evaluationNagMenu.add(noneItem);
			add(evaluationNagMenu);
		}
		add(new JMenuItem(ActionFactory.getCommentBeforeAction(game, textViewer, parent)));
		add(new JMenuItem(ActionFactory.getCommentAfterAction(game, textViewer, parent)));

		addSeparator();

		// results
		{
			JMenu setResultMenu = new JMenu("Set result");
			setResultMenu.add(new JMenuItem(ActionFactory.getResultAction(game, PGN.RESULT_WHITE_WINS)));
			setResultMenu.add(new JMenuItem(ActionFactory.getResultAction(game, PGN.RESULT_BLACK_WINS)));
			setResultMenu.add(new JMenuItem(ActionFactory.getResultAction(game, PGN.RESULT_DRAW)));
			setResultMenu.add(new JMenuItem(ActionFactory.getResultAction(game, PGN.RESULT_UNFINISHED)));
			add(setResultMenu);
		}

		// PGN tags
		JMenu pgnTagsMenu = new JMenu("PGN tags");
		pgnTagsMenu.add(new JMenuItem(ActionFactory.getEventAction(game, parent)));
		pgnTagsMenu.add(new JMenuItem(ActionFactory.getSiteAction(game, parent)));
		pgnTagsMenu.add(new JMenuItem(ActionFactory.getDateAction(game, parent)));
		pgnTagsMenu.add(new JMenuItem(ActionFactory.getRoundAction(game, parent)));
		pgnTagsMenu.add(new JMenuItem(ActionFactory.getWhiteAction(game, parent)));
		pgnTagsMenu.add(new JMenuItem(ActionFactory.getBlackAction(game, parent)));
		pgnTagsMenu.add(new JMenuItem(ActionFactory.getEcoAction(game, parent)));
		pgnTagsMenu.add(new JMenuItem(ActionFactory.getFenAction(game, parent)));
		pgnTagsMenu.add(new JMenuItem(ActionFactory.getEventDateAction(game, parent)));
		pgnTagsMenu.add(new JMenuItem(ActionFactory.getWhitesEloAction(game, parent)));
		pgnTagsMenu.add(new JMenuItem(ActionFactory.getBlacksEloAction(game, parent)));
		add(pgnTagsMenu);
		addSeparator();

		add(ActionFactory.getPromoteVariationAction(game, parent));
		add(ActionFactory.getPromoteToMainlineAction(game, parent));
		addSeparator();

		add(ActionFactory.getNullMoveAction(game, parent));
		addSeparator();

		add(ActionFactory.getDeleteRemainingMovesAction(game, textViewer));
		add(ActionFactory.getDeleteThisVariationAction(game, textViewer));
		addSeparator();

		add(ActionFactory.getStripAllCommentsAction(game, textViewer));
		add(ActionFactory.getStripAllVariationsAction(game, textViewer));
		add(ActionFactory.getStripAllNagsAction(game, textViewer));
		add(ActionFactory.getStripAllAction(game, textViewer));
	}
}

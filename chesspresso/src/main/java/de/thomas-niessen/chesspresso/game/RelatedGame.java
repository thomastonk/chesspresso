package chesspresso.game;

import chesspresso.position.Position;

/*
 * This interface is currently only used only for the difficult relation between Game
 * and Position. When a game is constructed, its Position object needs a back reference, 
 * which is set by Position::setRelatedGame. Within this method the compatibility is checked
 * by means of boolean checkCompatibility(Position pos). The back reference is needed to
 * inform the Game object about certain changes (see ChangeType) which are induced at the 
 * Position object, and this is done by positionChanged(ChangeType type, short move, String fen).
 */
@SuppressWarnings("preview")
public sealed interface RelatedGame permits Game {

	enum ChangeType {
		MOVE_DONE, MOVE_UNDONE, START_POS_CHANGED
	}

	boolean checkCompatibility(Position pos);

	void positionChanged(ChangeType type, short move, String fen);
}

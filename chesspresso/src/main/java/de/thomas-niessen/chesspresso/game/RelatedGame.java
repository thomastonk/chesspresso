package chesspresso.game;

import chesspresso.position.Position;
import chesspresso.position.PositionListener;

/*
 * This interface is used only for the difficult relation between Game and Position. 
 */
public sealed interface RelatedGame extends PositionListener permits Game {

	boolean checkCompatibility(Position pos);
}

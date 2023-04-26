# Set-Card-Game
Set is a real-time card game.
The game contains a deck of 81 cards. Each card contains a drawing with four features (color, number, shape, shading).
The game starts with 12 drawn cards from the deck that are placed on a 3x4 grid on the table.
The goal of each player is to find a combination of three cards from the cards on the table that are said to make up a “legal set”.
A “legal set” is defined as a set of 3 cards, that for each one of the four features — color, number, shape, and shading — the three cards must display that feature as either:
all the same or all different.
The possible values of the features are:
  ▪ The color: red, green or purple.
  ▪ The number of shapes: 1, 2 or 3.
  ▪ The geometry of the shapes: squiggle, diamond or oval.
  ▪ The shading of the shapes: solid, partial or empty.
The game's active components contain the dealer and the players. 
The players play together simultaneously on the table, trying to find a legal set of 3 cards by placing tokens on the cards.
If the set is not legal, the player gets a penalty, freezing his ability of removing or placing his tokens for a specified time period.
If the set is a legal set, the dealer will discard the cards that form the set from the table, replace them with 3 new cards from the deck and give the successful player one point. 
In this case the player also gets frozen although for a shorter time period.
The game will continue as long as there is a legal set to be found in the remaining cards. 
When there is no legal set left, the game will end and the player with the most points will be declared as the winner.

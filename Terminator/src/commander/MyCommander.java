package commander;
///////////////////Adding this comment to check if file linking is the right way to modify files
import java.io.*;
import java.util.*;

import com.aisandbox.cmd.*;
import com.aisandbox.cmd.info.*;
import com.aisandbox.cmd.cmds.*;
import com.aisandbox.util.*;
/**
 * Sample "balanced" commander for the AI sandbox. One bot attacking, one
 * defending and the rest randomly searching the level for enemies.
 * 
 * @author Matthias F. Brandstetter
 */
public class MyCommander extends SandboxCommander {
	/** For debugging */
    public static GameStateSaver stateSaver = null;
    /** a square that has risk higher than is is considered dangerous */
    public static int HIGH_RISK = 300;
    private static Random rand = new Random();
    static int nrTicks;
    private String myTeam;
    private String enemyTeam;

    String onSpotMissionName = "onspot";
    String onAttackSpotMissionName = "onAttackSpot";

    int nrLivingBots = 0;
    int nrAttackers = 0;
    int nrDefenders = 0;
    int nrFlagCarriers = 0;
    int nrFlagInterceptors = 0;
    int nrBots;
    int nrLivingEnemies;
    int nrMyTeamKilled = 0;
    int nrEnemyTeamKilled = 0;

    
    List<MyBotInfo> myBots = new ArrayList<MyBotInfo>();
    List<MyBotInfo> enemyBots = new ArrayList<MyBotInfo>();
    List<EnemyBotInfo> enemyLocations = new ArrayList<EnemyBotInfo>();
    Map<String, MyBotInfo> allBots = new HashMap<String, MyBotInfo>();
    
    //Author: Inge Becht. Define enemy bots with MyBotInfo
    List<MyBotInfo> myEnemyBots = new ArrayList<MyBotInfo>();
    
    /** set of names of all visible enemies */
    Set<String> visibleEnemies = new HashSet<String>();

    /** How often each square has been visited by an enemy */
    int[][] visitedByEnemy;
    int totalNrVisitedByEnemy;

    /** How often each square has been under attack by an enemy */
    int[][] attackedByEnemy;
    int totalNrAttackedByEnemy;

    /** How often each square is under attack by an enemy using defend */
    int[][] timesDefendedByEnemy;
    Vector2 myFlagSpawnLocation;
    Vector2 enemyFlagLocation;
    Vector2 myFlagLocation;
    Vector2 myFlagScoreLocation;
    Vector2 enemyFlagScoreLocation;
    Vector2 enemyFlagSpawnLocation;

    /** Location from which we will defend our flag */
    Vector2 myFlagDefendLocation;
    Vector2 myBotSpawnLocation;
    Vector2 enemyBotSpawnLocation;
    int firingDistance;
    //Vector2 center;
    
    /** Tile on which my flag is located */
    Tile myFlagTile;

    /** The status of our flag */
    FlagStatus myFlagStatus;

    /** Which of our bots is carrying the enemy flag (null if none) */
    MyBotInfo myFlagCarrier;

    /** The tile that helpers of the flag carrier should aim for */
    Tile flagCarrierHelperTarget;

    /** walkable tiles */
    boolean[][] walkable;

    /** the total number of tiles that are walkable */
    int nrWalkableTiles;

    /** width of the field */
    int width;

    /** height of the field */
    int height;

    /** cost of every square is 1 */
    int[][] unitCost;

    /** cost of every square is related to risk for my team to go over that square */
    int[][] riskBasedCost;

    /** As riskBasedCost, but for the flag carrier (tries to actively avoid enemy bots) */
    int[][] riskBasedCostForFlagCarrier;

    /** strategic values of squares (high values near flags, score locations etc) */
    int[][] strategicValue;

    /** Current game time in milliseconds */
    int currTimeMs;
    /** When we last saw each tile (game time stamp in milliseconds) */
    int[][] lastSeenMs;
    

    int gameSpeed = 1000;
    Distance shortestDistToMyBotSpawnLocation;
    Distance shortestDistToEnemyBotSpawnLocation;
    Distance shortestDistToMyFlagLocation;
    Distance shortestDistToMyFlagScoreLocation;
    Distance shortestDistToMyFlagSpawnLocation;
    Distance shortestDistToEnemyFlagLocation;
    Distance shortestDistToEnemyFlagScoreLocation;
    Distance shortestDistToEnemyFlagSpawnLocation;

    /** Contains the goal for attackers */
    Vector2 attackGoal;
    Distance[] strategicDistances;
    Graph graph = new Graph();
    SpotDefenders myFlagDefenders = new SpotDefenders();
    SpotDefenders enemyFlagDefenders = new SpotDefenders();

    /** List of ambush points to attack enemies */
    List<AmbushPoint> ambushPoints = new ArrayList<AmbushPoint>();

    /** Distance to nearest ambush point */
    Distance ambushDistance;

    /** Time stamp when we calculated ambush points (no need to calculate every tick) */ 
    float timeOfLastAmbushCalculation;
    /**
     * Set of all known enemy defenders
     */
    Defenders enemyDefenders = new Defenders(this);
    Interception flagInterception;
    
    // Enumeration for different shown behaviours.
    public enum Behaviours{FLAG_DEFEND, FLAG_ATTACK, DELIVER_FLAG, FLAG_ASSISTENCE, MISC, AMBUSH, PATROL, STALK, ENEMY_ATTACK};
    public enum gameState{FLAGS_BASE, ENEMY_CAPTURED, OWN_CAPTURED, BOTH_CAPTURED};
   
    // For every bot contains its last enum state so that transitions can be found
    Dictionary<String, Behaviours> last_behaviour = new Hashtable<String, Behaviours>();
    
    
    // The random forest classifier
    Classifier RF;

    /** Commands issued this tick (for debugging) */
    List<BotCommand> issuedCmds = new ArrayList<BotCommand>();
    public static final float SMALL_DIST = 1.51f;
    private static final Vector2[] smallCircleVectors = new Vector2[] {
        new Vector2(-SMALL_DIST, -SMALL_DIST),
            new Vector2(-SMALL_DIST, SMALL_DIST),
            new Vector2(SMALL_DIST, SMALL_DIST),
            new Vector2(SMALL_DIST, -SMALL_DIST),
    };

    /**
     * Custom commander class constructor.
     */
    public MyCommander() {
        name = "Terminator";

    }
    
    /*
     * Author: Inge Becht
     * Returns one of the following game states:
     * Flags at their spawn points (base)
     * Own flag captured (not at base)
     * Enemy flag captured (not at base)
     * Both flags in game (not at base)
     * 
     */
    public gameState current_flagstate()
    {
    	FlagInfo f = gameInfo.getEnemyFlagInfo();
    	Map<String, Vector2> m = levelInfo.getFlagSpawnLocations();
    	String enemy_name = gameInfo.getEnemyTeam();
   
    	gameState b = gameState.FLAGS_BASE;
    	
    	// If both flag positions don't correspond with their spawn position, set BOTH_CAPTURED
    	if( f.getPosition() != m.get("Terminator") && f.getPosition() != m.get(enemy_name) )
    	{
    		b = gameState.BOTH_CAPTURED;
    	}
    	else if ( f.getPosition() == m.get(enemy_name))
    	{
    		b = gameState.ENEMY_CAPTURED;
    	}
    	else if (f.getPosition() == m.get("Terminator"))
    	{
    		b = gameState.OWN_CAPTURED;
    	}
		return b;
    }
    /*
     * Author: Inge Becht
     * Writes a string at a time to the specified file in path
     */
    public void write_to_file(String path, String Information, boolean new_behaviour)
    {
    	File f = new File(path);
    	File p = new File("../Terminator_Classifier/Data/Terminator/ALL.csv");
    	
    	// Create directory in case it doesn't exist
    	f.getParentFile().mkdirs();
    	p.getParentFile().mkdirs();
    	try {
			f.createNewFile();
			FileWriter fstream = new FileWriter(path, true);
			FileReader fstream2 = new FileReader(path);
			BufferedWriter out = new BufferedWriter(fstream);
			BufferedReader read = new BufferedReader(fstream2);
			// In case the file is still empty add information line
			if(read.readLine() == null)
			{	out.write("BotName, Behaviour, PositionX, PositionY, OrientationX, " +
					"OrientationY,  " + 
					"DistanceToEnemyFlag, DistanceToOwnFlag, " +
					"DistanceToEnemyBase, DistanceToOwnBase, " +
					"DistanceToEnemyFlagScore, DistanceToOwnFlagScore, " +
					"DistanceToEnemyFlagSpawn, DistanceToOwnFlagSpawn, " +
					"Visibility, DistanceToNearestEnemy, GameState\n");	
				out.newLine();
		
			}
			// In case of new behaviour add an extra newline
			if(new_behaviour){
				out.newLine();
			}
			
			out.write(Information);
			out.newLine();
			out.close();
			read.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    	
    	
    	//Also put information in single file for training purposes.

    	try {
			p.createNewFile();
			FileWriter fstream = new FileWriter("../Terminator_Classifier/Data/Terminator/ALL.csv", true);
			FileReader fstream2 = new FileReader("../Terminator_Classifier/Data/Terminator/ALL.csv");
			BufferedWriter out = new BufferedWriter(fstream);
			BufferedReader read = new BufferedReader(fstream2);
			// In case the file is still empty add information line
			if(read.readLine() == null)
			{	out.write("BotName, Behaviour, PositionX, PositionY, Orientation, " +
					"DistanceToEnemyFlag, DistanceToOwnFlag, " +
					"DistanceToEnemyBase, DistanceToOwnBase, " +
					"DistanceToEnemyFlagScore, DistanceToOwnFlagScore, " +
					"DistanceToEnemyFlagSpawn, DistanceToOwnFlagSpawn, " +
					"Visibility, DistanceToNearestEnemy, seesEnemy, GameState, gameTime\n");	
				out.newLine();
		
			}
			
			out.write(Information);
			out.newLine();
			out.close();
			read.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    	
    }
    
    /*
     * Author: Inge Becht
     *Calculates absolute distance between bot and position
     * 
     */
    public int calculate_distance(MyBotInfo bot, Vector2 pos)
    {
    	return bot.getDistance(pos);
    }
    
    /*
     * Author: Inge Becht
     * Writes information about the input bot to file
     * 
     */
    public void collect_data(Behaviours b, BotInfo bot)
    {

    	String path = "../Terminator_Classifier/Data/Terminator/";
    	
    	String bot_name = bot.getName();
    	Vector2 bot_pos = bot.getPosition();
    	gameState state = current_flagstate();

    	// All distances that need to be calculated
    	int distance_enemy_flag = 0, distance_my_flag = 0;
    	int distance_enemy_spawn = 0, distance_my_spawn = 0;
    	int distance_enemy_flagbase = 0, distance_me_flagbase = 0;
    	int distance_enemy_score = 0, distance_me_score = 0;
    	
    	int nearest_seen_enemy;
    
    	
    	nearest_seen_enemy = DistanceNearestVisibleEnemy(bot);
    	
    	if(bot.getTeam().equals(myTeam))
    	{
    			
        		
    			 	// Get distance to all different elements   	
    	        	distance_my_flag = shortestDistToMyFlagLocation.getDistance(bot_pos);   	
    	        	distance_enemy_flag = shortestDistToEnemyFlagLocation.getDistance(bot_pos);
    	        	
    	        	// Distance to enemy spawn location
    	        	distance_enemy_spawn = shortestDistToEnemyBotSpawnLocation.getDistance(bot_pos);
    	        	distance_my_spawn = shortestDistToMyBotSpawnLocation.getDistance(bot_pos);
    	        	
    	        	// Distance to flag sore positions
    	        	
    	        	distance_enemy_score = shortestDistToEnemyFlagScoreLocation.getDistance(bot_pos);
    	        	distance_me_score = shortestDistToMyFlagScoreLocation.getDistance(bot_pos);
    	        
    	        	// Distance to flagbases
    	        	distance_enemy_flagbase = shortestDistToEnemyFlagSpawnLocation.getDistance(bot_pos); 
    	        	distance_me_flagbase = shortestDistToEnemyFlagSpawnLocation.getDistance(bot_pos);
		
    	}
    	else{
   
           	// Get distance to all different elements   	
        	distance_my_flag = shortestDistToMyFlagLocation.getDistance(bot_pos);   	
        	distance_enemy_flag = shortestDistToEnemyFlagLocation.getDistance(bot_pos);
        	
        	// Distance to enemy spawn location
        	distance_enemy_spawn = shortestDistToEnemyBotSpawnLocation.getDistance(bot_pos);
        	distance_my_spawn = shortestDistToMyBotSpawnLocation.getDistance(bot_pos);
        	
        	// Distance to flag sore positions
        	
        	distance_enemy_score = shortestDistToEnemyFlagScoreLocation.getDistance(bot_pos);
        	distance_me_score = shortestDistToMyFlagScoreLocation.getDistance(bot_pos);
        
        	// Distance to flagbases
        	distance_enemy_flagbase = shortestDistToEnemyFlagSpawnLocation.getDistance(bot_pos); 
        	distance_me_flagbase = shortestDistToEnemyFlagSpawnLocation.getDistance(bot_pos);

    	}
    	
    	int sees_enemies = 0;
    	if(bot.getVisibleEnemies().size() >0)
    	{
    		sees_enemies = 1;
    	}
    	// Visibility of tile bot is on
    	Tile t = Tile.get( (int) bot.getPosition().getX(), (int) bot.getPosition().getY());
    	int visibility = t.getVisibilityPercentage();
    
    	// Orientation of the bot
    	Vector2 bot_facing = bot.getFacingDirection();
    	
    	
    	//In case bot is really one of my team 
    	// TODO: fix distToNearestEnemy
    	
    	// Current game time, good for sighting data
        currTimeMs = (int)(1000*gameInfo.getMatchInfo().getTimePassed());

    	//System.out.printf(" Distance to my flag spawn %d\n", distance_me_flagbase );
    	//System.out.printf(" Distance to my flag deliver %d\n", distance_me_score);
    	
    	//System.out.println();
    	
    	// Construct a string containing all information necessary for classification
    	String information = bot_name.substring(bot_name.length()-1) + ", " + b.ordinal() + ", "  + 
    			(int)bot_pos.getX() + ", " + (int) bot_pos.getY() + 
    		 ", " + Float.toString((float) Utils.getFacingAngle(bot_facing.getX(), bot_facing.getY())) +
    			", " + distance_enemy_flag + ", " + distance_my_flag + 
    			", " + distance_enemy_spawn + ", " + distance_my_spawn +
    			", " + distance_enemy_score + ", " + distance_me_score + 
    			", " + distance_enemy_flagbase + ", " + distance_me_flagbase + 
    			", " + visibility + ", " + nearest_seen_enemy + ", " + sees_enemies + 
      			", " + state.ordinal() + ", " + currTimeMs;
    	
    	// Check if a switch in behaviour has occured
    	Behaviours past_b = last_behaviour.remove(bot.getName());
    	boolean new_behaviour;
    	new_behaviour = (b == past_b) ? false: true;
    
    	// Update new last behaviour
    	last_behaviour.put(bot.getName(), b);
    	
    	String b_string = b.toString();
    	path += b_string +"/" + bot_name + "_" + ".csv";
    	write_to_file(path, information, new_behaviour);
    }

    
    /*
     * Author: Inge Becht
     * Returns tiles that can be reached from t and are walkable
     */
    public List<Tile> possible_actions(Tile p)
    {
    	List<Tile> actions = new ArrayList<Tile>();
    	Tile temp;
    	// Check for all possible movement if it is valid
    	if(Tile.isValid(p.x + 1, p.y))
    	{
    		temp = Tile.get(p.x + 1, p.y);
    		if(temp.isWalkable){
    			actions.add(temp);
    		}
    	}
    	if(Tile.isValid(p.x - 1, p.y))
    	{
    		temp = Tile.get(p.x - 1, p.y);
    		if(temp.isWalkable){
    			actions.add(temp);
    		}
    	}
    	if(Tile.isValid(p.x, p.y + 1))
    	{
    		temp = Tile.get(p.x, p.y + 1);
    		if(temp.isWalkable){
    			actions.add(temp);
    		}
    	}
    	if(Tile.isValid(p.x, p.y - 1))
    	{
    		temp = Tile.get(p.x, p.y - 1);
    		if(temp.isWalkable){
    			actions.add(temp);
    		}
    	}
    	
		return actions;
    }
    
    /*
     * Author: Inge Becht
     * Check if tile t is contained within the Arraylist q
     */
    public boolean contains(List<Tile> h, Tile t)
    {
    	for(Tile l: h)
    	{
    		if(l.x == t.x && t.y == l.y)
    		{
    			return true;
    		}
    	}
    	
    	return false;
    }
    
    /*
     * Author: Inge Becht
     * Print elements from a list
     */
    public void print_list(List<Tile> elements)
    {
    	for(Tile element: elements)
    	{
    		System.out.printf("(%d, %d)\n", element.x, element.y);
    	}
    }
     public void print_stack(Stack<Tile> elements)
     {
    	 for(Tile element: elements )
    	 {
    		 System.out.printf("(%d, %d) ", element.x, element.y);
    	 }
    	 System.out.println();
     }
    
    public void print_queue(Queue<Stack<Tile>> q)
    {
    	for(Stack<Tile> t: q)
    	{
    		print_stack(t);
    		
    	}
    }
    
    /*
    public Stack<Tile> a_star(Vector2 begin, Vector2 end)
    {
    	Tile end_tile = Tile.get(end);
    	List<Tile> closed_set = new ArrayList<Tile>();
    	Comparator<Stack<Tile>> comparator = new ValueComparator(Tile.get(end));
    	PriorityQueue<Stack<Tile>> open_set = new PriorityQueue<Stack<Tile>>(11, comparator);
    	List<Tile> open_set2 = new ArrayList<Tile>();
    	Stack<Tile> s = new Stack<Tile>();
    	s.add(Tile.get(begin));
    	open_set.add(s);
    	open_set2.add(Tile.get(begin));
    	closed_set.add(Tile.get(begin));
    	
    	while(open_set.size() > 0)
    	{
    		Stack<Tile> p = open_set.poll();
    		if(p.peek().x == end_tile.x && p.peek().y == end_tile.y)
    		{
    			return p;
    		}
    		else{
    			closed_set.add(p.peek());
    			List<Tile> actions = possible_actions(p.peek());
    			for(Tile action: actions)
    			{
    				int tentative_g = p.size() + 1;
    				if(contains(closed_set, action))
    				{
    					if(tentative_g >= )
    					{
    						continue;
    					}
    				}
    				if(!contains(open_set2, action) || tentative_g >= ValueComparator.g(action))
    				{
    					
    					p.add(action);
    					
    					open_set.add((Stack<Tile>) p.clone());
    					open_set2.add(action);
    					p.pop();
    					
    				}
    			}
    		}
    	}
    	return null;
    }
    */
    /*
     * Author: Inge Becht
     * Creates a breadth first search for finding the distance between two particular tiles. 
     */
    public List<Tile> breadth_first(Vector2 begin, Vector2 end){
    	Tile t_end = Tile.get(end);
    	
    	Stack<Tile> path = new Stack<Tile>();
    	path.add(Tile.get(begin));
    	
    	// Keeps history of visited tiles
    	List<Tile> h = new ArrayList<Tile>();
    	h.add(Tile.get(begin));
    	
    	// Keeps trajectory
    	Queue<Stack<Tile>> q = new LinkedList<Stack<Tile>>();
    	q.add(path);
    	
    	while(q.size() != 0)
    	{
    		//System.out.println("Content of queue:");
    		//print_queue(q);
    		Stack<Tile> p = q.poll();
    		//System.out.println("Remove from queuq:");
    		//print_stack(p);
    		// If the last element of the first in the queue is the end
    		// point, return this path
    		if(p.peek().x == t_end.x && p.peek().y == t_end.y){
    			return p;
    		}
    		else{
    			// returns possible actions that can be made from the last position
    			List<Tile> actions = possible_actions(p.peek());
    			for(Tile action: actions)
    			{
    				//System.out.printf("Possible new actions from (%d, %d): ", p.peek().x, p.peek().y);
    				//print_list(actions);
    				
    				if(!contains(h, action)){
    					
    				
    				//	System.out.println("This action is new");
    					
    					p.add(action);
    					
    					q.add((Stack<Tile>) p.clone());
    					h.add(action);
    					p.pop();
    				}
    			}
    		}
    	}
    	
    	return null;
    	
    }
    
    
    /**
     * Called when the server sends the "initialize" message to the commander.
     * Use this function to setup your bot before the game starts. You can also
     * set this.verbose = true to get more information about each bot visually.
     */
    @Override
    public void initialize() {
        try {
            System.err.println("initialize");
            nrTicks = 0;
            // set the name of our and the enemy teams
            myTeam = gameInfo.getTeam();
            enemyTeam = gameInfo.getEnemyTeam();
            nrBots = gameInfo.getBots().size()/2;
            nrLivingEnemies = nrBots;
            Log.log("Nr bots: " + nrBots + ", nrAttackers: " + nrAttackers + ", nrDefenders: " + nrDefenders);
            for (int i = 0; i < BotInfo.STATE_HOLDING; ++i) {
                System.err.println("Fov " + Utils.stateToString(i) + ": " + levelInfo.getFovAngle(i));
            }
            System.err.println("firing distance:" + levelInfo.getFiringDistance());
            System.err.println("walking speed: " + levelInfo.getWalkingSpeed());
            System.err.println("running speed: " + levelInfo.getRunningSpeed());
            System.err.println("size: " + levelInfo.getWidth() + "x" + levelInfo.getHeight());
            // Calculate flag positions and store the middle.

            enemyFlagLocation = gameInfo.getEnemyFlagInfo().getPosition();
            myFlagLocation = gameInfo.getMyFlagInfo().getPosition();
            myFlagStatus = FlagStatus.STANDING_STILL;
            myFlagScoreLocation = levelInfo.getFlagScoreLocations().get(myTeam);
            myFlagSpawnLocation = levelInfo.getFlagSpawnLocations().get(myTeam);
            myBotSpawnLocation = Utils.getCenter(levelInfo.getBotSpawnAreas().get(myTeam));
            enemyBotSpawnLocation = Utils.getCenter(levelInfo.getBotSpawnAreas().get(enemyTeam));
            enemyFlagScoreLocation = levelInfo.getFlagScoreLocations().get(enemyTeam);
            enemyFlagSpawnLocation = levelInfo.getFlagSpawnLocations().get(enemyTeam);
            firingDistance = (int)levelInfo.getFiringDistance();
            flagInterception = new Interception(this);
            timeOfLastAmbushCalculation = -1000;
            // display the command descriptions next to the bot labels
            verbose = true;
            
            // Initialise the Random Forest Classifier
            RF = new Classifier();
        
            TeamInfo team = gameInfo.getMyTeamInfo();
            List<String> team_members = team.getMembers();
            for (String member: team_members)
            {
            	last_behaviour.put(member, Behaviours.MISC);
            }
           
           
            
            investigateWorld();
            System.err.println("leaving initialize");
        } catch (RuntimeException e) {
            e.printStackTrace();
            if (Utils.DEBUG) {
                throw e;
            }
        }
        
    }

    void investigateWorld() {
        // Get width and height
        width = levelInfo.getWidth();
        height = levelInfo.getHeight();

        // Add width height and firing distance in tile object
        Tile.init(width, height, (int)levelInfo.getFiringDistance());
        walkable = new boolean[width][height];
        nrWalkableTiles = 0;

        for (int i = 0; i < width; ++i) {
            int[] heights = levelInfo.getBlockHeights()[i];
            for (int j = 0; j < height; ++j) {
                walkable[i][j] = heights[j] == 0;
                Tile t = Tile.get(i, j);
                t.isWalkable = walkable[i][j];
                // Shootable is in case length 0 or 1?
                t.isShootable = heights[j] < 2;
           
                // Increment number of walkable tiles
                if (t.isWalkable) {
                    ++nrWalkableTiles;
                }
            }
        }
        
       
        
 
   
        int firingDistance = (int)levelInfo.getFiringDistance();
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                Tile t = Tile.get(i, j);
                // Calculates which tiles are shootable from a given tile
                t.calcAttackedTiles(firingDistance, true);
                t.calcAttackedTiles(30, false);
            }
        }
        // All variables used for enemy tracking
        totalNrVisitedByEnemy = 0;
        visitedByEnemy = new int[width][height];
        totalNrAttackedByEnemy = 0;
        attackedByEnemy = new int[width][height];
        timesDefendedByEnemy = new int[width][height];

        // unitCost used to determine cost per tile
        // (depending on enmy activity) 
        unitCost = new int[width][height];
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                unitCost[i][j] = 1;
            }
        }

        riskBasedCost = new int[width][height];
        riskBasedCostForFlagCarrier = new int[width][height];
        lastSeenMs = new int[width][height];

        // Create a map that calculates ditances from one point to every other
        // point from the map.
        shortestDistToMyBotSpawnLocation = calcWalkDistance(Utils.getCenter(levelInfo.getBotSpawnAreas().get(myTeam)), unitCost);
        shortestDistToEnemyBotSpawnLocation= calcWalkDistance(Utils.getCenter(levelInfo.getBotSpawnAreas().get(enemyTeam)), unitCost);
        shortestDistToMyFlagLocation = calcWalkDistance(myFlagLocation, unitCost);
        shortestDistToEnemyFlagLocation = calcWalkDistance(enemyFlagLocation, unitCost);
        shortestDistToMyFlagScoreLocation = calcWalkDistance(myFlagScoreLocation, unitCost);
        shortestDistToMyFlagSpawnLocation = calcWalkDistance(myFlagSpawnLocation, unitCost);
        shortestDistToEnemyFlagScoreLocation = calcWalkDistance(enemyFlagScoreLocation, unitCost);
        shortestDistToEnemyFlagSpawnLocation = calcWalkDistance(levelInfo.getFlagSpawnLocations().get(enemyTeam), unitCost);

        strategicDistances = new Distance[] {
            shortestDistToMyFlagLocation,
                //shortestDistToMyFlagScoreLocation,
                //shortestDistToEnemyFlagLocation,
                //shortestDistToEnemyFlagScoreLocation,
                //shortestDistToEnemyFlagSpawnLocation,
        };
        
        //Write information about walkable, distance to POI and visibility to file
        //MAPINFO
        
        String path = "../Terminator_Classifier/Data/Terminator/MapInfo.csv";
    	File f = new File(path);
    	File p = new File("../Terminator_Classifier/Data/Terminator/ALL.csv");
    	
    	// Create directory in case it doesn't exist
    	f.getParentFile().mkdirs();
    	p.getParentFile().mkdirs();
    
    	try {
			f.createNewFile();
			FileWriter fstream = new FileWriter(path, true);
			FileReader fstream2 = new FileReader(path);
			BufferedWriter out = new BufferedWriter(fstream);
			BufferedReader read = new BufferedReader(fstream2);
			// In case the file is still empty add information line
			
				out.write("PositionX, PositionY, is_walkable, " +
					"is_shootable,  " + 
					"DistanceToEnemyFlag, DistanceToOwnFlag, " +
					"DistanceToEnemyBase, DistanceToOwnBase, " +
					"DistanceToEnemyFlagScore, DistanceToOwnFlagScore, " +
					"DistanceToEnemyFlagSpawn, DistanceToOwnFlagSpawn, " +
					"Visibility");	
				out.newLine();

				int distance_enemy_flag = 0, distance_my_flag = 0;
		    	int distance_enemy_spawn = 0, distance_my_spawn = 0;
		    	int distance_enemy_flagbase = 0, distance_me_flagbase = 0;
		    	int distance_enemy_score = 0, distance_me_score = 0;
		    	

		    	int tile_walkable = 0;
		        int tile_shootable = 0;
		        	
		       	// Determine walkability of each tile
		        for (int i = 0; i < width; ++i) {
		            int[] heights = levelInfo.getBlockHeights()[i];
		            for (int j = 0; j < height; ++j) {
		                walkable[i][j] = heights[j] == 0;
		                Tile t = Tile.get(i, j);
		                t.isWalkable = walkable[i][j];
		                // Shootable is in case length 0 or 1?
		                t.isShootable = heights[j] < 2;
		                if (t.isWalkable)
		                {
		                	 tile_walkable = 1;
		                }
		                if(t.isShootable)
		                {
		                	tile_shootable =1;
		                }
		            	// Get distance to all different elements   	
			        	distance_my_flag = shortestDistToMyFlagLocation.getDistance(t.x,t.y);
			        	distance_enemy_flag = shortestDistToEnemyFlagLocation.getDistance(t.x, t.y);
			        	
			        	// Distance to enemy spawn location
			        	distance_enemy_spawn = shortestDistToEnemyBotSpawnLocation.getDistance(t.x, t.y);
			        	distance_my_spawn = shortestDistToMyBotSpawnLocation.getDistance(t.x, t.y);
			        	
			        	// Distance to flag sore positions
			        	
			        	distance_enemy_score = shortestDistToEnemyFlagScoreLocation.getDistance(t.x, t.y);
			        	distance_me_score = shortestDistToMyFlagScoreLocation.getDistance(t.x, t.y);
			        
			        	// Distance to flagbases
			        	distance_enemy_flagbase = shortestDistToEnemyFlagSpawnLocation.getDistance(t.x, t.y); 
			        	distance_me_flagbase = shortestDistToEnemyFlagSpawnLocation.getDistance(t.x, t.y);
			        
			     		String information =
			     		(int) t.x +", " + (int) t.y + ", "  + 
	     				tile_walkable + ", " + tile_shootable +
            			", " + distance_enemy_flag + ", " + distance_my_flag + 
            			", " + distance_enemy_spawn + ", " + distance_my_spawn +
            			", " + distance_enemy_score + ", " + distance_me_score + 
            			", " + distance_enemy_flagbase + ", " + distance_me_flagbase + 
            			", " + t.getVisibilityPercentage() ;
			     		out.write(information);
						out.newLine();
						tile_walkable = 0;
						tile_shootable = 0;
		                // Increment number of walkable tiles
		                if (t.isWalkable) {
		                    ++nrWalkableTiles;
		                }
		            }
		        }
		        out.close();
		        read.close();		 
			
		} catch (IOException e) {
			e.printStackTrace();
		}

        
        // Initialise first values by simulating an attack by enemy by first
        // calculating path to our flag and then their score base.
        prepareAttackedByEnemy();
        buildGraph();
    }

    /*
     * Author: Inge Becht
     * Check if string is in the list
     */
    public boolean is_in_list(List<String> list, String s)
    {
    	for(String string: list)
    	{
    		if(string.equalsIgnoreCase(s))
    		{
    			return true;
    		}
    	}
    	return false;
    }
    public boolean is_in_list(List<Tile> list, Tile t)
    {
    	for(Tile lt: list)
    	{
    		if(lt.x == t.x && lt.y == t.y)
    		{
    			return true;
    		}
    	}
    	return false;
    }
    
    /*
     * Author: Inge Becht
     * Gives list of tiles that are seen at that moment
     */
    public List<Tile> seen_tiles()
    {
    	List<Tile> seen = new ArrayList<Tile>();
    
    	for(MyBotInfo b: myBots)
    	{
  
    		float r = levelInfo.getFovAngle(b.bot.getState());
    		Tile t = Tile.get(b.bot.getPosition());
    		double facing_angle = Utils.getFacingAngle(b.bot.getFacingDirection());
    		double angles[] = t.seenAngles;
    		Tile[] seenTiles= t.seenTiles; 
    		 for (int i = 0; i < seenTiles.length; ++i) {
    	         if (Utils.isWithinAngle(angles[i], facing_angle, b.bot.getState())) {
    	         }
    	         	if(!is_in_list(seen, seenTiles[i]))
    	         	{
    	         		seen.add(seenTiles[i]);
    	         	}
    	        	 
    	         }

    	}
    	return seen;
   
    }
 

    //------------------------------------------------------------------------------------------------------
    // TICK
    //------------------------------------------------------------------------------------------------------


    /**
     * Called when the server sends a "tick" message to the commander. Override
     * this function for your own bots. Here you can access all the information
     * in this.gameInfo, which includes game information, and this.levelInfo
     * which includes information about the level. You can send commands to your
     * bots using the issue() method in this class.
     */
    @Override
    public void tick() {
        ++nrTicks;
        
        TeamInfo team = gameInfo.getMyTeamInfo();
        List<String> bot_names = team.getMembers();
       
        
        List<String> already_added = new ArrayList<String>();
        
        // Loop through my bots
        for(String bot_name: bot_names)
        {
       
        	BotInfo bot_info= gameInfo.getBotInfo(bot_name);
        	
        	List<String> visible_enemies = bot_info.getVisibleEnemies();
        	// All seen bots need to be collected data on (but only if not done already)
        	for(String enemy_name: visible_enemies)
        	{
        		if(is_in_list(already_added, enemy_name)) continue;
        		else{
        			already_added.add(enemy_name);
        			collect_data(Behaviours.MISC, gameInfo.getBotInfo(enemy_name));
        		}
        	}
        }
        Map<String, Vector2> m = levelInfo.getFlagSpawnLocations();
        Vector2 b= m.get(team.getName());
        System.out.printf("Vector: %f %f\n", b.getX(), b.getY());
        System.out.printf("V: %f %f\n",  enemyFlagSpawnLocation.getX(), enemyFlagSpawnLocation.getY());
        // Tiles that can be seen at that tick.
        // TODO: What do i doooo with these?
        List<Tile> seen_tiles = seen_tiles();
        
        try {
          myTick();
        } catch (RuntimeException e) {
            e.printStackTrace();
            System.err.println("Exception at tick " + nrTicks);
            if (Utils.DEBUG) {
                throw e;
            }
        }
    }

    public void myTick() {
        long now = System.currentTimeMillis();
        currTimeMs = (int)(1000*gameInfo.getMatchInfo().getTimePassed());
        Log.log("tick " + nrTicks + ", time: " + gameInfo.getMatchInfo().getTimePassed());

        // Set flag locations
        myFlagLocation = gameInfo.getMyFlagInfo().getPosition();
        enemyFlagLocation = gameInfo.getEnemyFlagInfo().getPosition();

        // Clear all cmds, no issues should be running
        issuedCmds.clear();

        // Handles combat, does not really seem interesting for now
        handleCombatEvents();

        // Update all tiles that were seen
        updateLastSeen();
        
        // update list of defenders in case of kill event
        updateEnemyDefenders();

    
        calcDistances();
        findAmbushPoints();
        setMyBots();
        // attack visible enemies
        //attackVisibleEnemies();
        int defenders = 0;
        int attackers = 0;
        int flagCarriers = 0;
        int flagInterceptors = 0;
        boolean allRolesAssigned = false;
        while (!allRolesAssigned) {
            MyBotInfo bot;
            int nrAssigned = 0;
            if (flagInterceptors < nrFlagInterceptors) {
                bot = selectBotForRole(MyBotRole.FLAG_INTERCEPTOR, MyBotInfo.DIST_TO_FLAG_INTERCEPTION_COMPARATOR);
                if (bot != null) {
                    if (bot.flagInterceptInfo.pathToInterception == null) {
                        // bot cannot intercept the flag, restore bot role
                        bot.proposedRole = MyBotRole.OTHER;
                    } else {
                        flagInterceptors++;
                        nrAssigned ++;
                    }
                }
            }
            if (defenders < nrDefenders) {
                bot = selectBotForRole(MyBotRole.DEFENDER, MyBotInfo.DIST_TO_MY_FLAG_COMPARATOR);
                if (bot != null) {
                    defenders ++;
                    nrAssigned ++;
                    if (defenders <= 1 && attackers <= 1) {
                        if (Tile.get(myFlagLocation).distance.getDistance(bot.bot.getPosition()) 
                                > Tile.get(enemyFlagLocation).distance.getDistance(bot.bot.getPosition())) {
                            // only 1 defender, but it is closer to enemy flag, turn it to an attacker instead
                            bot.proposedRole = MyBotRole.ATTACKER;
                            defenders--;
                            attackers++;
                                }
                    }
                }
            }
            if (attackers < nrAttackers) {
                bot = selectBotForRole(MyBotRole.ATTACKER, MyBotInfo.DIST_TO_ENEMY_FLAG_COMPARATOR);
                if (bot != null) {
                    attackers ++;
                    nrAssigned ++;
                }
            }
            if (flagCarriers < nrFlagCarriers) {
                bot = selectBotForRole(MyBotRole.FLAG_CARRIER, MyBotInfo.DIST_TO_FLAG_SCORE_COMPARATOR);
                if (bot != null) {
                    flagCarriers ++;
                    nrAssigned ++;
                }
            }
            allRolesAssigned = nrAssigned == 0;
        }
        // give orders to all bots (only change when needed)
        for (MyBotInfo bot : myBots) {
            BotInfo botInfo = bot.bot;
            int currState = botInfo.getState();
            boolean roleChanged = bot.proposedRole != bot.realRole;
            if (!botInfo.getVisibleEnemies().isEmpty()) {
                Log.log("Bot SEES ENEMIES! " + bot.name + " proposed order " + bot.proposedRole + ", real: " + bot.realRole + ", state: " + Utils.stateToString(botInfo.getState()));
                for (String name : botInfo.getVisibleEnemies()) {
                    BotInfo enemy = gameInfo.getBotInfo(name);
                    boolean canSeeMe = enemy.getVisibleEnemies().contains(botInfo.getName());
                    Log.log("   enemy: " + enemy.getName() + ", can see me: " + canSeeMe + ", position: " + enemy.getPosition() + ", facing: " + enemy.getFacingDirection() + ", state: " + Utils.stateToString(enemy.getState()));
                }
            }
            if (currState == BotInfo.STATE_TAKING_ORDERS || currState == BotInfo.STATE_SHOOTING /*|| currState == BotInfo.STATE_HOLDING*/ || bot.isDead()) {
                if (currState == BotInfo.STATE_SHOOTING && bot.mission.equals(onSpotMissionName)) {
                    myFlagDefenders.addActiveDefender(bot);
                }
                if (currState == BotInfo.STATE_SHOOTING && bot.mission.equals(onAttackSpotMissionName)) {
                    enemyFlagDefenders.addActiveDefender(bot);
                }
                /*if (currState == BotInfo.STATE_HOLDING) {
                // hammer attack commands in holding state
                bot.enableNewCommand();
                issueAttackCmd(bot, bot.target, bot.mission);
                }*/
                continue;
            }
            if (Log.logging) {
                Log.log("Bot " + bot.name + " proposed order " + bot.proposedRole + ", real: " + bot.realRole + ", state: " + Utils.stateToString(botInfo.getState()) + ", mission: " + bot.mission);
            }
            switch (bot.proposedRole) {
                case ENEMY_ATTACKER:
                    BotInfo enemy = gameInfo.getBotInfo(bot.attackedEnemy);
                    float distance = enemy.getPosition().distance(botInfo.getPosition());
                    if (distance < levelInfo.getFiringDistance()) {
                        if (roleChanged || currState != BotInfo.STATE_DEFENDING) {
                            issueDefendCmd(bot, enemy.getPosition().sub(botInfo.getPosition()),
                                    "defend/attack enemy");
                        }
                    } else if (distance < levelInfo.getFiringDistance() + 2*levelInfo.getRunningSpeed()) {
                        if (roleChanged || currState != BotInfo.STATE_ATTACKING) {
                            issueAttackCmd(bot, enemy.getPosition(), "attack enemy");
                            collect_data(Behaviours.ENEMY_ATTACK, bot.bot);
                        }
                    } else if (roleChanged || currState != BotInfo.STATE_CHARGING) {
                        issueChargeCmd(bot, enemy.getPosition(), "charge enemy");
                        	collect_data(Behaviours.ENEMY_ATTACK, bot.bot);
                    }
                    break;
                case ATTACKER:
                    {
                        if (myFlagCarrier != null) {
                            // we are carrying the flag; attack flag spawn location and stay there
                            // so when/if we carried home the flag we will immediately pick it up again
                            /*Vector2 goal = levelInfo.getFlagSpawnLocations().get(enemyTeam);
                              if (goal.sub(botInfo.getPosition()).length() < 4f) {
                              turnDefendLocation(bot, goal, "enemy flag");
                              } else {
                              List<Tile> path = shortestDistToEnemyFlagSpawnLocation.getPathFrom(botInfo.getPosition());
                              issueChargeOrAttack(bot, path, "to flag spawn");
                              }*/
                            float distToGoal = attackGoal.distance(botInfo.getPosition());//int distToGoal = Tile.get(attackGoal).distance.getDistance(botInfo.getPosition());
                            if (distToGoal >= 1) {
                                if (bot.mission.equals(onAttackSpotMissionName)) {
                                    // flag carrier got probably close to home so attack target was changed;
                                    // important to react quickly
                                    bot.enableNewCommand();
                                }
                                List<Tile> path = bot.getPathTo(attackGoal);//distToAttackGoal.getPathFrom(botInfo.getPosition());
                                //issueChargeOrAttack(bot, path, "to flag spawn");
                                issueChargeOrAttack(bot, path, "ATTACK FLAG LIKE CRAY CRAY");
                                collect_data(Behaviours.FLAG_ATTACK, bot.bot);
                            } else if (botInfo.getState() == BotInfo.STATE_IDLE || bot.mission.equals(onAttackSpotMissionName)) {
                                if (botInfo.getPosition().distance(enemyFlagDefenders.defendLocation.toVector2()) < 1.2f) {
                                    // on defend location
                                    enemyFlagDefenders.addActiveDefender(bot);
                                } else {
                                    // on flag spawn
                                    //turnDefendLocation(bot, enemyFlagSpawnLocation, "enemy flag");
                                    turnDefendLocation(bot, enemyFlagSpawnLocation, "enemy flag");
                                    collect_data(Behaviours.FLAG_ATTACK, bot.bot);
                                }
                            }
                        } else {
                            List<Tile> path;
                            if (isWorldSafe() || (bot.rnd &7) <= 1) {
                                path = Tile.get(enemyFlagLocation).distance.getPathFrom(botInfo.getPosition());
                            } else {
                                path = bot.getPathTo(enemyFlagLocation);
                            }
                            int rnd = rand.nextInt(100); // high gamespeed: don't care too much about enemies
                            int distFromSpawn = Tile.get(myBotSpawnLocation).distance.getDistance(botInfo.getPosition());
                            int distToGoal = Tile.get(enemyFlagLocation).distance.getDistance(botInfo.getPosition());
                            if (distFromSpawn >= distToGoal || Math.min(gameSpeed, 50) > rnd || !stalkEnemyIfPossible(bot)) {
                                //List<Vector2> wayPoints = toVector2List(path);
                                //issueChargeOrAttack(bot, path, "run to enemy flag");
                                issueChargeOrAttack(bot, path, "ATTACK FLAG");
                            }
                        }
                    }
                    break;
                case DEFENDER:
                    {
                    	// Write to file
                    	collect_data(Behaviours.FLAG_DEFEND, bot.bot);
                        BotInfo defender = botInfo;
                        float requiredDist = 0.8f;
                        if (myFlagDefendLocation == myFlagLocation) {
                            requiredDist = 2f;
                        }
                        if (myFlagDefendLocation.sub(defender.getPosition()).length() > requiredDist) {
                            if (!stalkEnemyIfPossible(bot)) {
                                //Distance dist = distToMyFlagDefendLocation;
                                if ((bot.rnd & 1) == 1) {
                                    //dist = shortestDistToMyFlagDefendLocation;
                                }
                                List<Tile> path = bot.getPathTo(myFlagDefendLocation);//dist.getPathFrom(botInfo.getPosition());
                                if (defender.getPosition().distance(myFlagLocation) < 10) {
                                    List<Vector2> wayPoints = toVector2List(path, false);
                                    //issueAttackCmd(bot, wayPoints, myFlagLocation, "defend my flag");
                            
                                    issueAttackCmd(bot, wayPoints, myFlagLocation, "DEFEND LIKE CRAY CRAY");
                                } else {
                                    //issueChargeOrAttack(bot, path, "defend my flag");
                                    issueChargeOrAttack(bot, path, "DEFEND LIKE CRAY CRAY");
                                }
                            }
                        } else if (myFlagDefendLocation.distance(myFlagLocation) < 0.1) {
                            turnDefendLocation(bot, myFlagLocation, "my flag");
                        } else if (botInfo.getState() == BotInfo.STATE_IDLE || bot.mission.equals(onSpotMissionName)) {
                            // bot is at defend location. Double check if it really can see my flag
                            Tile t = Tile.get(myFlagDefendLocation);
                            boolean ok = true;
                            for (int a = -1; a <= 1; a+=2) {
                                for (int b = -1; b <= 1; b += 2) {
                                    if (Tile.isValid(t.x + a, t.y + b)) {
                                        Tile t2 = Tile.get(t.x+a, t.y+b);
                                        if (t2.isWalkable) {
                                            if (!Tile.isReallyVisible(defender.getPosition(), t2.toVector2())) {
                                                ok = false;
                                            }
                                        }
                                    }
                                }
                            }
                            if (ok /*Tile.isReallyVisible(defender.getPosition(), myFlagDefendLocation)*/) {
                                myFlagDefenders.addActiveDefender(bot);
                            } else {
                                // no! perhaps slight mislocation. Move a little bit towards the flag

                                float dist = myFlagDefenders.locationToDefend.distance(defender.getPosition());
                                Vector2 tgt = toWalkable(myFlagDefenders.locationToDefend);
                                if (dist > 1) {
                                    tgt = defender.getPosition().add(tgt.sub(defender.getPosition()).scale(2/dist));
                                }
                                //issueAttackCmd(bot, tgt, myFlagDefenders.locationToDefend, "correct defend location");
                                issueAttackCmd(bot, tgt, myFlagDefenders.locationToDefend, "DEFEND LIKE CRAY CRAY");
                            }
                        }
                    }
                    break;
                case FLAG_CARRIER:
                    {
                        String runHomeMission = "run home";
                        if (bot.hasFlag()) {
                            if (!bot.mission.equals(runHomeMission) 
                                    // very strange (bug in server?) that flag carrier sometimes changes state spontaneously
                                    // when the flag carrier nears its goal
                                    || (botInfo.getState() != BotInfo.STATE_MOVING && botInfo.getState() != BotInfo.STATE_CHARGING)) {
                                // tell the flag carrier to run home
                                Distance distToFlagScore = calcWalkDistance(myFlagScoreLocation, riskBasedCostForFlagCarrier);
                                List<Tile> safePath = distToFlagScore.getPathFrom(botInfo.getPosition());
                                List<Tile> path;
                                if (isWorldSafe()) {
                                    // We intend to take the shortest path home
                                    path = shortestDistToMyFlagScoreLocation.getPathFrom(botInfo.getPosition());
                                    // But check if respawn is a threat 
                                    int distToEnemySpawn = 10000;
                                    int distFlagCarrierToIntercept = 0;
                                    Tile enemyBotSpawn = Tile.get(enemyBotSpawnLocation);
                                    for (int i = 0; i < path.size(); ++i) {
                                        Tile t = path.get(i);
                                        int dist = enemyBotSpawn.distance.getDistance(t.x, t.y);
                                        if (dist < distToEnemySpawn) {
                                            distToEnemySpawn = dist;
                                            distFlagCarrierToIntercept = i;
                                        }
                                    }
                                    float timeToRespawn = gameInfo.getMatchInfo().getTimeToNextRespawn();
                                    float timeEnemyToIntercept = distToEnemySpawn/levelInfo.getRunningSpeed() + timeToRespawn - 2;
                                    float myTimeToIntercept = distFlagCarrierToIntercept/levelInfo.getRunningSpeed();
                                    if (timeEnemyToIntercept < myTimeToIntercept) {
                                        // respawned bots can intercept flag carrier, take safe way home instead
                                        Log.log("Take safe way home; timeEnemyToIntercept: " + timeEnemyToIntercept + ", myTime: " + myTimeToIntercept);
                                        path = safePath;//bot.getPathTo(myFlagScoreLocation);
                                    }
                                } else {
                                    path = safePath;//bot.getPathTo(myFlagScoreLocation);
                                }
                                List<Vector2> wayPoints = toVector2List(path, false);
                                // let it take immediate effect
                                bot.enableNewCommand();
                                //issueMoveCmd(bot, wayPoints, runHomeMission);
                                issueMoveCmd(bot, wayPoints, "RUN HOME LIKE CRAY CRAY");
                                collect_data(Behaviours.DELIVER_FLAG, bot.bot);
                                    }
                        } else {
                            // help flag carrier
                            //Distance dist = flagCarrierHelperTarget.distance;
                            List<Tile> path = bot.getPathTo(flagCarrierHelperTarget.toVector2());//dist.getPathFrom(botInfo.getPosition());
                            List<Vector2> wayPoints = toVector2List(path, true);
                            if (getTimeSinceLastCommand(bot) > 4) {
                                bot.enableNewCommand();
                                bot.mission = ""; // will force a new command
                            }
                            issueChargeCmd(bot, wayPoints, "HELP CARRIER LIKE CRAY CRAY");
                            collect_data(Behaviours.FLAG_ASSISTENCE, bot.bot);
                        }
                        //Vector2 target = levelInfo.getFlagScoreLocations().get(myTeam);
                        //issueMoveCmd(bot, target, "running home");
                    }
                    break;
                case FLAG_INTERCEPTOR: {
                                           InterceptInfo info = bot.flagInterceptInfo;
                                           Log.log("Interceptor " + bot.name + ": " + info);
                                           if (info.distToInterceptionPoint() < 4) {
                                               if (info.isBehindVictim) {
                                                   List<Vector2> wayPoints = new ArrayList<Vector2>();
                                                   wayPoints.add(myFlagLocation);
                                                   wayPoints.add(enemyFlagScoreLocation);
                                                   issueChargeCmd(bot, wayPoints, "intercept: run after");
                                               } else {
                                                   issueAttackCmd(bot, myFlagLocation, "intercept: await");
                                               }
                                           } else if (info.distToInterceptionPoint() +4 > info.victimDistToInterceptionPoint/2) {
                                               issueChargeCmd(bot, info.getInterceptionPoint().toVector2(), "intercept: to x");
                                           } else {
                                               Vector2 facingDir = myFlagLocation;
                                               if (info.victimDistToInterceptionPoint < 10) {
                                                   facingDir = null;
                                               }
                                               issueAttackCmd(bot, info.getInterceptionPoint().toVector2(), facingDir, "intercept: to x");
                                           }
                                           break;
                }
                case OTHER:
                                       if (!stalkEnemyIfPossible(bot)) {
                                           // go to nearest ambush point
                                           int distToClosestAmbush = ambushDistance.getDistance(botInfo.getPosition());
                                           if (distToClosestAmbush >= 1) {
                                               List<Tile> path = ambushDistance.getPathFrom(botInfo.getPosition());
                                               Tile target = path.get(path.size()-1);
                                               // calculate a secure path
                                               path = bot.getPathTo(target.toVector2());
                                               issueChargeOrAttack(bot, path, "to ambush");
                                               collect_data(Behaviours.AMBUSH, bot.bot);
                                           } else {
                                               Tile t = Tile.get(botInfo.getPosition());
                                               AmbushPoint ambushPoint = null;
                                               for (AmbushPoint point : ambushPoints) {
                                                   if (point.tile == t) {
                                                       ambushPoint = point;
                                                       break;
                                                   }
                                               }
                                               if (ambushPoint != null) {
                                                   Vector2 target = botInfo.getPosition().add(ambushPoint.bestFacingDirection);
                                                   //issueDefendCmd(bot, target, "ambush");
                                                   issueDefendCmd(bot, target, "ambush LIKE CRAY CRAY");
                                                   collect_data(Behaviours.AMBUSH, bot.bot);
                                               } else {
                                                   //throw new RuntimeException("Tile " + t + " is not an ambush point, bot = " + bot);
                                                   // should never happen
                                                   Vector2 target = levelInfo
                                                       .findRandomFreePositionInBox(levelInfo.getLevelArea());
                                                   //issueChargeOrAttack(bot, target, "random patrol");
                                                   issueChargeOrAttack(bot, target, "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXxrandom patrol LIKE CRAY CRAY");
                                                   collect_data(Behaviours.PATROL, bot.bot);
                                               }
                                           }
                                       }
                                       break;
                default:
                                       break;
            }
        }
        //spotDefend(myFlagDefenders, onSpotMissionName);
        spotDefend(myFlagDefenders, "DEFENDING LIKE CRAY CRAY");
        spotDefend(enemyFlagDefenders, onAttackSpotMissionName);
        updateBotsPostTick();
        logBoard();
        Log.log("tick took: " + (System.currentTimeMillis()-now) + " ms");
        if (stateSaver != null) {
            stateSaver.saveGameState(this);
        }
    }

    /**
     * Determine to which tile attackers should go. Normally they go for the enemy flag,
     * but when we are carrying the flag they go the flag spawn location or a place nearby.
     * Will set distToAttackGoal.
     */
    private void determineAttackGoal() {
        if (myFlagCarrier == null) {
            attackGoal = enemyFlagLocation;
        } else {
            // if flag carrier is almost home, attackers should aim at enemy flag spawn location,
            // otherwise they should hide at a safe spot near the enemy flag
            int distToHome = Tile.get(myFlagCarrier.bot.getPosition()).distance.getDistance(myFlagScoreLocation);
            Log.log("dist to home = " + distToHome);
            if (distToHome <= 2*firingDistance+10) {
                attackGoal = enemyFlagSpawnLocation;//distToAttackGoal = distToEnemyFlagSpawnLocation;
                Log.log("attack goal = enemy flag spawn location");
            } else {
                Log.log("attack goal = enemy flag defend spot at " + enemyFlagDefenders.defendLocation);
                attackGoal = enemyFlagDefenders.defendLocation.toVector2();
            }
        }
    }

    private void spotDefend(SpotDefenders defenders, String missionName) {
        if (defenders.isNewCommandsNeeded()) {
            Log.log("Recalculating spot defenders for spot " + defenders.spotToDefend + ", nr defenders = " + defenders.getDefenders().size());
            Wheel wheel = new Wheel();
            wheel.setFovAngle(levelInfo.getFovAngle(BotInfo.STATE_DEFENDING));
            initWheelWithStrategicValues(defenders.getDefendLocation(), wheel, strategicValue);
            int val = 15;
            for (MyBotInfo bot : defenders.getDefenders()) {
                // let it take immediate effect
                bot.enableNewCommand();
                //if (defenders.getDefenders().size() < 1) {
                // 1 defender: aim at flag
                //issueDefendCmd(bot, defenders.spotToDefend.toVector2(), missionName);
                //} else {
                Vector2 facingDirection = wheel.getBestFacingDirection();
                Vector2 target = bot.bot.getPosition().add(facingDirection);
                issueDefendCmd(bot, target, missionName);
                int segmentNr = wheel.getBestSegmentNr();
                wheel.markUsed(segmentNr, val);
                --val;
                //}
            }
        }

    }
    private MyBotInfo selectBotForRole(MyBotRole role, Comparator botComparator) {
        Collections.sort(myBots, botComparator);
        int n = 0;
        for (int i = 0; n < 1 && i < myBots.size(); ++i) {
            MyBotInfo bot = myBots.get(i);
            if (bot.proposedRole == MyBotRole.OTHER) {
                bot.proposedRole = role;
                return bot;
            }
        }
        return null;
    }

    //------------------------------------------------------------------------------------------------------
    // SETMYBOTS
    //------------------------------------------------------------------------------------------------------

    /*
     * Author: Inge Becht
     * We want some MyBotInfo knowledge for the opponent
     * bots. Here we fill myEnemyBots with that information 
     */
    void setMyEnemyBots()
    {
    	for (BotInfo bot : gameInfo.getBots().values()) {
            if (bot.getTeam().equals(enemyTeam)) {
                MyBotInfo myBot = new MyBotInfo();
                myBot.name = bot.getName();
                myBot.realRole = myBot.proposedRole = MyBotRole.DEAD;
                myBot.timeOfLastOrder = -1000;
                myBot.bot = bot;
                myEnemyBots.add(myBot);
              
            }
        }
       // calcMyEnemyBotDistances();
    }
    
    void setMyBots() {
        if (myBots.isEmpty()) {
            allBots.clear();
            for (BotInfo bot : gameInfo.getBots().values()) {
                if (bot.getTeam().equals(myTeam)) {
                    MyBotInfo myBot = new MyBotInfo();
                    myBot.name = bot.getName();
                    myBot.realRole = myBot.proposedRole = MyBotRole.DEAD;
                    myBot.timeOfLastOrder = -1000;
                    myBot.bot = bot;
                    myBots.add(myBot);
                    allBots.put(bot.getName(), myBot);
                }
            }
            calcMyBotDistances();
        }
        if (enemyBots.isEmpty()) {
            enemyLocations.clear();
            for (BotInfo bot : gameInfo.getBots().values()) {
                if (bot.getTeam().equals(enemyTeam)) {
                    MyBotInfo myBot = new MyBotInfo();
                    myBot.name = bot.getName();
                    myBot.realRole = MyBotRole.OTHER;
                    myBot.timeOfLastOrder = -1000;
                    enemyBots.add(myBot);
                    allBots.put(bot.getName(), myBot);
                    EnemyBotInfo enemyBot = new EnemyBotInfo();
                    enemyBot.name = bot.getName();
                    enemyLocations.add(enemyBot);
                }
            }
        }
        nrLivingBots = 0;
        myFlagCarrier = null;
        visibleEnemies.clear();
        flagCarrierHelperTarget = Tile.get(enemyFlagLocation);
        // check if we carry the flag
        for (MyBotInfo myBot : myBots) {
            BotInfo bot = gameInfo.getBotInfo(myBot.name);
            myBot.bot = bot;
            if (bot.getHealth() > 0 && bot.hasFlag()) {
                myBot.proposedRole = MyBotRole.FLAG_CARRIER;
                myFlagCarrier = myBot;
                // Let flag carrier helpers aim to the tile that is 15 closer to the score location
                // than the flag carrier itself
                List<Tile> pathToScore = shortestDistToMyFlagScoreLocation.getPathFrom(bot.getPosition());
                int d = 15;
                if (pathToScore.size() <= d) {
                    flagCarrierHelperTarget = Tile.get(bot.getPosition());
                } else {
                    flagCarrierHelperTarget = pathToScore.get(d);
                }
            }
        }
        calcMyBotDistances();
        determineAttackGoal();
        int COST_TO_SWITCH_ROLE = 3*HIGH_RISK;
        for (MyBotInfo myBot : myBots) {
            BotInfo bot = gameInfo.getBotInfo(myBot.name);
            ++nrLivingBots;
            if (bot.getHealth() <= 0) {
                myBot.proposedRole = myBot.realRole = MyBotRole.DEAD;
                --nrLivingBots;
                myBot.mission = "";
                myBot.enableNewCommand();
            } else if (bot.hasFlag()) {
                myBot.proposedRole = MyBotRole.FLAG_CARRIER;
                myFlagCarrier = myBot;
                myBot.distToFlagCarrierHelperTarget = 0;
            } else {
                myBot.proposedRole = MyBotRole.OTHER;
                myBot.distToAttackGoal = myBot.getDistance(attackGoal);//distToAttackGoal.getDistance(bot.getPosition());
                myBot.distToMyFlag = myBot.getDistance(myFlagDefendLocation);//distToMyFlagDefendLocation.getDistance(bot.getPosition());
                // TODO: use path finding to calc distance instead of simple distance
                myBot.distToFlagCarrierHelperTarget = flagCarrierHelperTarget.squareDist(Tile.get(bot.getPosition()));
                if (myBot.realRole == MyBotRole.ATTACKER) {
                    myBot.distToAttackGoal -= COST_TO_SWITCH_ROLE;
                } else if (myBot.realRole == MyBotRole.DEFENDER) {
                    myBot.distToMyFlag -= COST_TO_SWITCH_ROLE;
                    if (myBot.mission != null && myBot.mission.equals(onSpotMissionName)) {
                        // extra cost for switching from defend state
                        myBot.distToMyFlag -= 3*COST_TO_SWITCH_ROLE;
                    }
                } else if (myBot.realRole == MyBotRole.FLAG_CARRIER) {
                    myBot.distToFlagCarrierHelperTarget -= COST_TO_SWITCH_ROLE;
                }
            }
            if (bot.getState() == BotInfo.STATE_IDLE) {
                myBot.enableNewCommand();
            }
            for (String visibleEnemyName : bot.getVisibleEnemies()) {
                visibleEnemies.add(visibleEnemyName);
            }
        }
        // update which tiles are visited/seen by the enemy
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                timesDefendedByEnemy[i][j] = 0;
            }
        }
        for (MyBotInfo myBot : enemyBots) {
            BotInfo bot = gameInfo.getBotInfo(myBot.name);
            myBot.bot = bot;
            if (bot.getHealth() > 0) {
                float lastSeenTime = gameInfo.getMatchInfo().getTimePassed() - bot.getSeenLast();
                if (Math.abs(lastSeenTime - myBot.previousSeenLastTime) > 0.1f) {
                    if (myBot.previousPosition != null && myBot.previousPosition.distance(bot.getPosition()) > 2) {
                        // speculate that bot took shortest path from previous known location
                        // to current location
                        Distance dist = Tile.get(bot.getPosition()).distance;
                        List<Tile> visitedTiles = dist.getPathFrom(myBot.previousPosition);
                        updateAttackedByEnemy(visitedTiles);
                        Log.log("Update attacked tiles for " + myBot.name +
                                ", path: " + Utils.toString(myBot.previousPosition) + "-" + Utils.toString(bot.getPosition()));
                    } else {
                        ++visitedByEnemy[(int)bot.getPosition().getX()][(int)bot.getPosition().getY()];
                        ++totalNrVisitedByEnemy;
                        // update attacked squares
                        visitAttackedTiles(bot, new UpdateAttackedTilesVisitor());
                    }
                }
            }
            boolean isNearFlag = enemyFlagLocation.distance(enemyFlagSpawnLocation) < 2
                && enemyFlagLocation.distance(bot.getPosition()) < levelInfo.getFiringDistance();
            if (bot.getState() == BotInfo.STATE_DEFENDING) {
                Defender defender = new Defender(bot);
                enemyDefenders.add(defender, isNearFlag);
            } else if (bot.getState() != BotInfo.STATE_SHOOTING) {
                Defender defender = enemyDefenders.getDefender(bot.getName());
                if (defender != null) {
                    // strange: a shooting defender can have state idle
                    if (defender.creationTime + 1000 < currTimeMs 
                            || bot.getState() == BotInfo.STATE_ATTACKING
                            || bot.getState() == BotInfo.STATE_CHARGING
                            || bot.getState() == BotInfo.STATE_DEAD
                            || bot.getState() == BotInfo.STATE_MOVING) {
                        enemyDefenders.remove(bot.getName());
                            }
                }
                /*} else if (isNearFlag) {
                  int lastSeen = timeSinceLastSeen(Tile.get(bot.getPosition()));
                  Log.log("Shooting enemy at " + bot.getPosition() + ", lastSeen tile: " + lastSeen);
                  if (lastSeen > 7000) {
                // the bot is shooting near enemy flag, we haven't seen the bot for a long time;
                // it could actually be a defender. Let's assume for a while that it is one
                Defender defender = new Defender(bot);
                enemyDefenders.addPotentialFlagDefender(defender);
                }*/
                }
        } 
        // update squares defended by enemies
        if (nrLivingEnemies > 0) {
            for (Defender defender: enemyDefenders.getDefenders()) {
                if (defender.facingDirection != null) {
                    Tile botTile = Tile.get(defender.location);
                    double facingAngle = Utils.getFacingAngle(defender.facingDirection);
                    botTile.visitAttackedTiles(facingAngle, levelInfo.getFovAngle(BotInfo.STATE_DEFENDING), new UpdateDefendedTilesVisitor());
                }
            }
        }
        nrDefenders = 0;
        nrAttackers = 1;
        nrFlagCarriers = 0;
        nrFlagInterceptors = 0;
        if (nrLivingBots <= 2) {
            if (nrLivingEnemies >= 3) {
                nrDefenders = 1;
                nrAttackers = Math.max(0, nrLivingBots - nrDefenders);
            } else {
                nrAttackers = nrLivingBots;
                nrDefenders = 0;
            }
        } else if (nrLivingBots <= 5) {
            nrDefenders = 1;
            nrAttackers = nrLivingBots - 1;
        } else if (nrLivingBots <= 8) {
            nrDefenders = 2;
            nrAttackers = nrLivingBots - 3;
        } else if (nrLivingBots <= 11) {
            nrDefenders = 3;
            nrAttackers = nrLivingBots - 5;
        } else {
            nrDefenders = 4;
            nrAttackers = nrLivingBots - 4 - nrLivingBots/4;
        }
        if (myFlagCarrier != null) {
            if (nrAttackers > 2) {
                nrAttackers = 2 + (nrAttackers-2)/2;
            }
            if (nrDefenders > 2) {
                --nrDefenders;
            }
            nrFlagCarriers = Math.max(1, nrLivingBots - nrAttackers - nrDefenders - 1);
            if (nrLivingEnemies == 0 && nrLivingBots == 2) {
                // special case, no need for defenders
                nrDefenders = 0;
                nrFlagCarriers = 0;
            }  
        }
        // if my flag is being moved, we can decrease number of defenders
        if (myFlagStatus == FlagStatus.MOVING) {
            if (nrDefenders >= 2) {
                nrDefenders = nrDefenders/2;
            }
            // try to intercept the flag
            nrFlagInterceptors = Math.max(1, nrLivingBots - nrAttackers - nrDefenders - nrFlagCarriers);
            // calculate which bots can intercept the flag, and if yes, when
            List<Tile> pathFromFlagToScore = shortestDistToEnemyFlagScoreLocation.getPathFrom(myFlagLocation);
            flagInterception.update(pathFromFlagToScore);
            Log.log("Proposed nrFlagInterceptors: " + nrFlagInterceptors);
            for (MyBotInfo b : myBots) {
                if (b.proposedRole == MyBotRole.OTHER) {
                    flagInterception.calcInterceptInfo(Tile.get(b.bot.getPosition()), b.flagInterceptInfo);
                    if (b.flagInterceptInfo.getInterceptionPoint() != null) {
                        Log.log("Bot " + b.name + " can intercept: " + b.flagInterceptInfo);
                    }
                } else {
                    b.flagInterceptInfo.reset();
                }
            }
        }
        // special case if no enemies are alive
        if (nrLivingEnemies == 0) {
            // no need for many flag carriers if no enemies are alive
            nrFlagCarriers = Math.max(1, nrFlagCarriers);
            nrAttackers = Math.max(2, nrAttackers);
            nrDefenders = Math.max(1, nrDefenders);
        }
        String proposal = String.format("Proposal: defenders: %d, attackers: %d, flagCarriers: %d, interceptors: %d", 
                nrDefenders, nrAttackers, nrFlagCarriers, nrFlagInterceptors);
        Log.log(proposal);
        // set "high risk"
        HIGH_RISK = 300;
        if (gameSpeed < 50 && nrLivingEnemies >= nrLivingBots - 2 && nrLivingEnemies >= 5) {
            HIGH_RISK = 250 + gameSpeed;
        }

    }

    //------------------------------------------------------------------------------------------------------
    // DISTANCES/RISK
    //------------------------------------------------------------------------------------------------------

    void calcDistances() {
        calcRiskBasedCost();
        setStrategicValues();
        enemyDefenders.calcAttackedSquares();
        //shortestDistToEnemyFlagLocation = calcWalkDistance(enemyFlagLocation, unitCost);
        //distToEnemyFlagLocation = calcWalkDistance(enemyFlagLocation, riskBasedCost);
        //distToMyFlagScoreLocation = calcWalkDistance(levelInfo.getFlagScoreLocations().get(myTeam), riskBasedCost);
        //distToEnemyFlagSpawnLocation = calcWalkDistance(levelInfo.getFlagSpawnLocations().get(enemyTeam), riskBasedCost);
        Tile myFlagTile = Tile.get(myFlagLocation);
        // if my flag is on the move, we defend our flag spawn location instead
        if (myFlagStatus == FlagStatus.MOVING) {
            myFlagTile = Tile.get(myFlagSpawnLocation);
        }
        int[][] cost = Utils.copyArray(riskBasedCost);
        int spawnToFlagDist = shortestDistToMyBotSpawnLocation.getDistance(myFlagLocation);
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                int distToMyBotSpawn = shortestDistToMyBotSpawnLocation.getDistance(i, j);
                cost[i][j] += distToMyBotSpawn - spawnToFlagDist;
            }
        }
        Tile defendSpot = myFlagTile.findDefendSpot(cost, 500, 1000);
        myFlagDefenders.update(myFlagLocation, defendSpot);
        if (myFlagTile != defendSpot) {
            myFlagDefendLocation = myFlagDefenders.getDefendLocation().toVector2();
        } else {
            // there is no good spot to defend from; defend from flag
            myFlagDefendLocation = myFlagLocation;
        }
        //distToMyFlagDefendLocation = calcWalkDistance(myFlagDefendLocation, riskBasedCost);
        Tile attackSpot = Tile.get(enemyFlagSpawnLocation).findDefendSpot(cost, 500000, firingDistance);
        enemyFlagDefenders.update(enemyFlagSpawnLocation, attackSpot);
        //calcMyBotDistances();
    }

    private void calcMyBotDistances() {
        for (MyBotInfo myBot : myBots) {
            if (!myBot.isDead() && myBot.bot.getState() != BotInfo.STATE_TAKING_ORDERS && myBot.bot.getState() != BotInfo.STATE_SHOOTING) {
                myBot.distance = new GDistance(graph);
                myBot.distance.addInitial(Tile.get(myBot.bot.getPosition()).node);
                myBot.distance.calcDistances(400);
            }
        }
    }

    // Location = position you want the distance to calculate to 
    // cost = cost of every tile 
    private Distance calcWalkDistance(Vector2 location, int[][] cost) {
        // inittialise dist object
        Distance dist = new Distance(walkable, cost);
        dist.addInitial(location);
        dist.calcDistances(400);
        return dist;
    }

    // For every tile determine the cost depending on risks.
    // These risks depend on how visible the tiles are and how often an attack
    // took place there.
    void calcRiskBasedCost() {
        float respawnTime = gameInfo.getMatchInfo().getTimeToNextRespawn();
        if (respawnTime > 0) {
            int n = (int)((100*myBots.size())/(respawnTime+1));
            if (n > 0 && n < gameSpeed) {
                gameSpeed = n;
                System.err.println("Need for speed: " + gameSpeed + ", nrBots: " + myBots.size() + ", respawn time: " + respawnTime);
            }
        }
        double averageAttacksPerSquare = ((double)totalNrAttackedByEnemy)/nrWalkableTiles;
        if (averageAttacksPerSquare < 0.0001) {
            averageAttacksPerSquare = 1;
        }
        int maxNrAttackable = Tile.maxNrAttackable;
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                // add risk for going over tiles often seen by enemy
                double attackRisk = (70 * attackedByEnemy[i][j]) / averageAttacksPerSquare;
                attackRisk = Math.min(attackRisk, 260 + gameSpeed);
                // add risk for highly visible tiles
                Tile t = Tile.get(i, j);
                int visibilityRisk = (100 * t.attackedTiles.length) / maxNrAttackable;
                riskBasedCost[i][j] = 200 + (int)attackRisk + visibilityRisk;
            }
        }
        // add risk for tiles near enemy spawn
        Tile enemyBotSpawn = Tile.get(enemyBotSpawnLocation);
        for (Tile t : enemyBotSpawn.attackedTiles) {
            riskBasedCost[t.x][t.y] += 3*gameSpeed;
        }
        // add risk for defended tiles
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                riskBasedCost[i][j] += 2000*timesDefendedByEnemy[i][j];
            }
        }
        // set risk for flag carrier
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                riskBasedCostForFlagCarrier[i][j] = Math.max(10, riskBasedCost[i][j] - Math.max(gameSpeed, 150));
                // better with squares near own bot spawn location
                riskBasedCostForFlagCarrier[i][j] += Tile.get(i, j).distance.getDistance(myBotSpawnLocation);
            }
        }
        // add risk for tiles attacked by other enemies (flag carrier only)
        for (MyBotInfo bot : enemyBots) {
            BotInfo botInfo = bot.bot;
            if (botInfo.getSeenLast() < 0.2f && botInfo.isAlive() && botInfo.getState() != BotInfo.STATE_DEFENDING) {
                Tile t = Tile.get(botInfo.getPosition());
                double angle = Utils.getFacingAngle(botInfo.getFacingDirection());
                t.visitSeenTiles(angle, levelInfo.getFovAngle(BotInfo.STATE_CHARGING), new TileVisitor(){

                    @Override
                    public boolean visit(Tile t) {
                        riskBasedCostForFlagCarrier[t.x][t.y] += 100; 
                        return true;
                    }

                });
            }
        }
        // update edge values of the graph
        for (Node n : graph.nodes) {
            for (Edge e : n.neighbours) {
                Tile t1 = (Tile)e.from.object;
                Tile t2 = (Tile)e.to.object;
                int dist = 100;
                if (t1.x != t2.x && t1.y != t2.y){
                    dist = 141;
                }
                int cost = (riskBasedCost[t1.x][t1.y] + riskBasedCost[t2.x][t2.y])/2; 
                e.value = (dist*cost)/100;
            }
        }
    }

    public void setStrategicValues() {
        // set strategic values of squares
        double averageVisitsPerSquare = ((double)totalNrVisitedByEnemy)/nrWalkableTiles;
        if (averageVisitsPerSquare < 0.0001) {
            averageVisitsPerSquare = 1;
        }
        strategicValue = new int[width][height];
        Tile myFlagTile = Tile.get(myFlagLocation);
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                Tile t = Tile.get(i, j);
                strategicValue[i][j] = t.getVisibilityPercentage();
                int distToMyFlagSq = myFlagTile.squareDist(t);
                strategicValue[i][j] += Math.max(0, 40*(20-distToMyFlagSq));
                if (t == myFlagTile) {
                    strategicValue[i][j] += 1000;
                }
                int amountVisited = Math.max(1000, (int)(100*visitedByEnemy[i][j]/averageVisitsPerSquare));
                strategicValue[i][j] += amountVisited/5;
            }
        }
    }

    /**
     * Update time stamp of all tiles seen by my bots.
     */
    void updateLastSeen() {
        // In case all enemies are dead act if all tiles are seen
        if (nrLivingEnemies == 0) {
            for (int i = 0; i < width; ++i) {
                for (int j = 0; j < height; ++j) {
                    lastSeenMs[i][j] = currTimeMs; 
                }
            }
        } else {
            // Else update for all tiles that are seen the timestamp.
            for (MyBotInfo myBot : myBots) {
                BotInfo bot = gameInfo.getBotInfo(myBot.name);
                Tile t = Tile.get(bot.getPosition());
                double angle = Utils.getFacingAngle(bot.getFacingDirection());
                t.visitSeenTiles(angle, levelInfo.getFovAngle(BotInfo.STATE_CHARGING), new TileVisitor(){

                    @Override
                    public boolean visit(Tile t) {
                        lastSeenMs[t.x][t.y] = currTimeMs; 
                        return true;
                    }

                });
            }
        }
    }

    void updateEnemyDefenders() {
        // remove defenders whose location we see (and the bot is not there)
        /*Set<String> removedDefenders = new HashSet<String>();
          for (Defender defender : enemyDefenders.getAllDefenders()) {
          if (!defender.isFlagDefender()) {
          Tile t = Tile.get(defender.location);
          int lastSeenTime = timeSinceLastSeen(t);
          if (lastSeenTime == 0) {
          BotInfo bot = gameInfo.getBotInfo(defender.name);
          if (bot != null && bot.getSeenLast() > 1) {
          removedDefenders.add(defender.name);
          }
          }
          }
          }
          for (String name : removedDefenders) {
          enemyDefenders.remove(name);
          }*/
        // add "potential flag defender" if one of our bot is killed near enemy flag
        for (MatchCombatEvent event : gameInfo.getMatchInfo().getCombatEvents()) {
            Log.log("Combat event " + Utils.combatEventToString(event.getType()) + ", subject: "
                    + event.getSubject());
            if (event.getType() == MatchCombatEvent.TYPE_BOT_KILLED) {
                String killedBotName = event.getSubject();
                BotInfo killedBot = gameInfo.getBotInfo(killedBotName);

                // If bot of my team was killed
                if (killedBot.getTeam().equals(myTeam)) {
                    boolean isNearFlag = enemyFlagLocation.distance(enemyFlagSpawnLocation) < 2
                        && enemyFlagLocation.distance(killedBot.getPosition()) < levelInfo.getFiringDistance();

                    // In case flag is almost delivered and the distance of bot
                    // kill position to flag is closer than fire distance
                    if (isNearFlag) {
                        
                        // Get information about who killed the bot
                        BotInfo killingBot = gameInfo.getBotInfo(event.getInstigator());
                        if (enemyDefenders.getDefender(killingBot.getName()) == null) {
                            // the enemy bot is shooting near enemy flag
                            // it could actually be a defender. Let's assume for a
                            // while that it is one
                            Defender defender = new Defender(killingBot);
                            defender.creationTime = currTimeMs;
                            // Add newly created defender as a flag defender
                            enemyDefenders.add(defender, false);// don't mark is at a flag defender
                        }
                    }
                }
            }
        }
    }

    /**
     * Return how many milliseconds ago we last saw the given tile.
     * @param t
     * @return
     */
    public int timeSinceLastSeen(Tile t) {
        return currTimeMs - lastSeenMs[t.x][t.y];
    }

    void updateBotsPostTick() {
        for (MyBotInfo myBot : enemyBots) {
            BotInfo bot = gameInfo.getBotInfo(myBot.name);
            if (bot.getHealth() > 0) { 
                myBot.previousPosition = bot.getPosition();
                myBot.previousSeenLastTime = gameInfo.getMatchInfo().getTimePassed() - bot.getSeenLast();
            } else {
                myBot.previousPosition = null;
            }
        } 
    }

    private boolean isWorldSafe() {
        return nrLivingEnemies <= 1;
    }

    private boolean isHighRisk(int x, int y) {
        return riskBasedCost[x][y] >= HIGH_RISK;
    }

    //------------------------------------------------------------------------------------------------------
    // COMMANDS
    //------------------------------------------------------------------------------------------------------


    private boolean chargeOrAttack(MyBotInfo bot) {
        boolean charge = bot.bot.getVisibleEnemies().isEmpty();
        if (charge) {
            charge = nrLivingBots >= nrLivingEnemies*2 || nrLivingEnemies <= 2;
            if (!charge && nrLivingBots >= nrLivingEnemies) {
                charge = bot.bot.getVisibleEnemies().isEmpty();
                if (charge) {
                    int val = (70*nrLivingEnemies)/nrLivingBots;
                    charge = rand.nextInt(100) > val;
                }
            }
        }
        return charge;
    }

    private void issueChargeOrAttack(MyBotInfo bot, List<Tile> targets, String mission) {
        // Make some general decision depending of the characteristics of the game
        AttackOrChargeSelector selector = new AttackOrChargeSelector();
        int command = selector.analyze(targets, riskBasedCost);
        List<Tile>segment = selector.getPathSegment();
        int movePerc = Math.min(15, (gameSpeed-100)/15);
        /*if (command == BotInfo.STATE_ATTACKING && needForSpeed > 100) {
        // in very fast games we can decide more often to move/charge
        if (rand.nextInt(100) <= movePerc) {
        command = BotInfo.STATE_CHARGING;
        } else if (rand.nextInt(100) <= movePerc) {
        //command = BotInfo.STATE_MOVING;
        }
        }*/
        //if (needForSpeed < 100) {
        int distToClosestEnemy = distToNearestEnemy(bot);
        if (distToClosestEnemy < levelInfo.getFiringDistance() + 2*levelInfo.getRunningSpeed()) {
            // if we see an enemy we attack, regardless of result of analysis
            command = BotInfo.STATE_ATTACKING;
        } else if (nrLivingBots >= nrLivingEnemies*2 || (nrLivingEnemies <= 2 && nrLivingBots >= nrLivingEnemies)) {
            // if world is quite safe and we see no enemies we charge anyway
            command = BotInfo.STATE_CHARGING;
        }
        //}
        // check if the segment contains squares that are in range of defenders.
        // We want attack such squares, looking at the defender.
        Defender defender = null;
        int firstDefendedIndex = -1;
        if (nrLivingEnemies > 0) {
            for (int i = 0; i < segment.size(); ++i) {
                Tile t = segment.get(i);
                Defender def = enemyDefenders.getDefender(t);
                if (def != null) {
                    firstDefendedIndex = i;
                    defender = def;
                    break;
                }
            }
        }
        Vector2 lookAt = null;
        if (defender != null) {
            if (firstDefendedIndex > 5) {
                List<Tile> seg2 = new ArrayList<Tile>();
                for (int i = 0; i < firstDefendedIndex -2; ++i) {
                    seg2.add(segment.get(i));
                }
                segment = seg2;
                defender = null;
            } else {
                command = BotInfo.STATE_ATTACKING;
                lookAt = defender.location;
                int lastDefendIndex = segment.size()-1;
                for (int i = firstDefendedIndex + 1; i < segment.size(); ++i) {
                    Tile t = segment.get(i);
                    Defender def = enemyDefenders.getDefender(t);
                    if (def == null) {
                        // stop immediately after we leave defended squares.
                        while (i+1 < segment.size()) {
                            segment.remove(i+1);
                        }
                    }
                }
            }
        }
        // a charge command that leads to an attack command should be finished a bit earlier since the bot will
        // "slide" a bit while going from charge to attack , and will be vulnerable while taking orders
        if (segment.size() > 4 && segment.size() < targets.size() && command != BotInfo.STATE_ATTACKING) {
            segment.remove(segment.size()-1);
            segment.remove(segment.size()-1);
        }
        boolean addZigZag = (command == BotInfo.STATE_ATTACKING && lookAt == null);
        List<Vector2> wayPoints = toVector2List(segment, addZigZag);
        if (command == BotInfo.STATE_ATTACKING) {
            Vector2 optimalLookAt = lookAt;
            /*if (lookAt == null && rand.nextBoolean() && segment.size() > 1) {
              optimalLookAt = calcOptimalLookAt(segment);
            }
            Log.log("OptimalLookAt for bot " + bot.name + ": " + optimalLookAt);*/
            issueAttackCmd(bot, wayPoints, optimalLookAt, mission + " (attack)");
        } else if (command == BotInfo.STATE_CHARGING){
            issueChargeCmd(bot, wayPoints, mission + " (charge)");
        } else {
            issueMoveCmd(bot, wayPoints, mission + " (moving)");
        }
    }

    /*private void issueChargeOrAttack(MyBotInfo bot, List<Vector2> targets, String mission) {
      if (chargeOrAttack(bot)) {
      issueChargeCmd(bot, targets, mission + " (charge)");
      } else {
      issueAttackCmd(bot, targets, mission + " (attack)");
      }
      }*/

    private void issueChargeOrAttack(MyBotInfo bot, Vector2 target, String mission) {
        Tile t = Tile.get(target);
        List<Tile> path = t.distance.getPathFrom(bot.bot.getPosition());
        Log.log("issueChargeOrAttack, bot " + bot.name + ", tgt " + Utils.toString(target) + ", path: " + Utils.toString((List)path));
        issueChargeOrAttack(bot, path, mission);
        /*if (chargeOrAttack(bot)) {
          issueChargeCmd(bot, target, mission + " (charge)");
          } else {
          issueAttackCmd(bot, target, mission + " (attack)");
          }*/
    }

    private int distToNearestEnemy(MyBotInfo myBot) {
        Tile myLoc = Tile.get(myBot.bot.getPosition());
        int nearestDist = 1000000;
        for (MyBotInfo enemyBot : enemyBots) {
            BotInfo bot = enemyBot.bot;
            if (bot.getHealth() > 0 && bot.getSeenLast() < 1) {
                int dist = myLoc.distance.getDistance(bot.getPosition());
                if (dist < nearestDist) {
                    nearestDist = dist;
                }
            }
        }
        return nearestDist;
    }

    private void turnDefendLocation(MyBotInfo bot, Vector2 location, String goal) {
        BotInfo botInfo = bot.bot;
        BotInfo nearestEnemy = findNearestVisibleEnemy(botInfo);
        boolean defend = false;
        if (nearestEnemy != null) {
            float dist = nearestEnemy.getPosition().distance(botInfo.getPosition());
            if (dist < levelInfo.getFiringDistance() + 3*levelInfo.getRunningSpeed()) {
                defend = true;
            }
        }
        if (defend) {
            bot.enableNewCommand(); // high importance to defend position
            issueAttackCmd(bot, nearestEnemy.getPosition(), "attack-defend " + goal);
        } else {
            List<Vector2> wayPoints = new ArrayList<Vector2>();
            float dist = 0.3f;
            Vector2 v2 = location.add(new Vector2(-dist, 0));
            Vector2 v3 = location.add(new Vector2(0, dist));
            Vector2 v4 = location.add(new Vector2(dist, 0));
            Vector2 v5 = location.add(new Vector2(0, -dist));
            boolean reverse = rand.nextBoolean();
            for (int i = 0; i < 300; ++i) {
                if (reverse) {
                    wayPoints.add(v5);
                    wayPoints.add(v4);
                    wayPoints.add(v3);
                    wayPoints.add(v2);
                } else {
                    wayPoints.add(v2);
                    wayPoints.add(v3);
                    wayPoints.add(v4);
                    wayPoints.add(v5);
                }
            }
           
            if(goal == "enemy flag")
            {
            	collect_data(Behaviours.FLAG_ATTACK, bot.bot);
            }
            else{
            	collect_data(Behaviours.FLAG_DEFEND, bot.bot);
            
            }
            issueAttackCmd(bot, wayPoints, null, "LIKE CRAY CRAY turn-defend " + goal);
        }
    }

    /**
     * Attacks a nearby enemy if there is one.
     * @param bot
     * @return true if an attack command was issued
     */
    private boolean stalkEnemyIfPossible(MyBotInfo bot) {
        BotInfo botInfo = bot.bot;
        BotInfo enemy;
        BotInfo nearestEnemy = null;
        Distance distance = Tile.get(bot.bot.getPosition()).distance;
        int nearestDistance = (int)(levelInfo.getFiringDistance() + 2*levelInfo.getRunningSpeed());
        for (MyBotInfo b : enemyBots) {
            enemy = b.bot;
            if (enemy.getHealth() > 0 && enemy.getSeenLast() >= 0 && enemy.getSeenLast() < 0.5 
                    /*&& enemy.getState() != BotInfo.STATE_TAKING_ORDERS*/) {
                int distToEnemy = distance.getDistance(enemy.getPosition());
                if (distToEnemy < nearestDistance) {
                    nearestEnemy = enemy;
                    nearestDistance = distToEnemy;
                }
                    }
        }
        //
        if (nearestEnemy != null && bot.proposedRole == MyBotRole.ATTACKER) {
            int myBotDistToGoal = Tile.get(botInfo.getPosition()).distance.getDistance(attackGoal);
            int enemyDistToGoal = Tile.get(nearestEnemy.getPosition()).distance.getDistance(attackGoal);
            if (myBotDistToGoal < enemyDistToGoal - firingDistance + 5) {
                // don't let attacker go away from goal to much
                return false;
            }
        }
        boolean result = false;
        if (nearestEnemy != null) {
            result = true;
            List<Tile> path = bot.getPathTo(nearestEnemy.getPosition());
            List<Vector2> wayPoints = toVector2List(path, false);
            float distUntilWeSeeEnemy = distUntilWeSeeEndpoint(
                    Utils.getFacingAngle(botInfo.getFacingDirection()), wayPoints);
            float distUntilEnemySeesUs = distUntilEnemySeesUs(nearestEnemy.getPosition(),
                    Utils.getFacingAngle(nearestEnemy.getFacingDirection()), wayPoints);
            Log.log("   Stalk enemy: " + bot.name + " attacks " + nearestEnemy.getName() + ", distWeSee: "
                    + distUntilWeSeeEnemy + ", distEnemySees: " + distUntilEnemySeesUs);
            if (distUntilWeSeeEnemy < distUntilEnemySeesUs) {
                // hunt the bot down, we will surprise him
                if (nrLivingEnemies >= 6 || gameSpeed > 100) {
                    issueAttackCmd(bot, wayPoints, null, "attackstalk " + nearestEnemy.getName());
                    collect_data(Behaviours.STALK, bot.bot);
                } else {
                    issueChargeCmd(bot, wayPoints, "stalk " + nearestEnemy.getName());
                    collect_data(Behaviours.STALK, bot.bot);
                }
            } else {
                // enemy bot and my bot on collision course; rather attack
                issueAttackCmd(bot, wayPoints, null, "face " + nearestEnemy.getName());
            }
        }
        return result;
    }

    private float distUntilWeSeeEndpoint(double facingAngle, List<Vector2> wayPoints) {
        if (wayPoints.size() <= 1) {
            return 0;
        }
        float dist = 0;
        Tile target = Tile.get(wayPoints.get(wayPoints.size()-1));
        double fovAngle = levelInfo.getFovAngle(BotInfo.STATE_ATTACKING);
        double angle = facingAngle;
        for (int i = 0; i < wayPoints.size() - 1; ++i) {
            Vector2 curr = wayPoints.get(i);
            Tile from = Tile.get(curr);
            if (from.attacks(angle, fovAngle, target)) {
                return dist;
            }
            Vector2 next = wayPoints.get(i+1);
            dist += next.distance(curr);
            angle = Utils.getAngleTo(curr.getX(), curr.getY(), next.getX(), next.getY());
        }
        return dist;
    }

    private float distUntilEnemySeesUs(Vector2 enemyPosition, double enemyFacingAngle, List<Vector2> wayPoints) {
        if (wayPoints.size() <= 1) {
            return 0;
        }
        float dist = 0;
        Tile enemyPos = Tile.get(enemyPosition);
        double fovAngle = levelInfo.getFovAngle(BotInfo.STATE_ATTACKING);
        double angle = enemyFacingAngle;
        for (int i = 0; i < wayPoints.size(); ++i) {
            Vector2 curr = wayPoints.get(i);
            Tile from = Tile.get(curr);
            if (enemyPos.attacks(angle, fovAngle, from)) {
                return dist;
            }
            if (i < wayPoints.size() - 1) {
                dist += curr.distance(wayPoints.get(i+1));
            }
        }
        return dist;
    }

    private void issueAttackCmd(MyBotInfo bot, Vector2 target, String mission) {
        issueAttackCmd(bot, target, null, mission);
    }

    private void issueAttackCmd(MyBotInfo bot, Vector2 target, Vector2 lookAt, String mission) {
        if (bot.bot.getState() != BotInfo.STATE_ATTACKING || !bot.mission.equals(mission)) {
            issueCmd(bot, new AttackCmd(bot.name, target, lookAt, mission), target, mission);
        }
    }
    private void issueAttackCmd(MyBotInfo bot, List<Vector2> targets, Vector2 facingDir, String mission) {
        if (bot.bot.getState() != BotInfo.STATE_ATTACKING || !bot.mission.equals(mission)) {
            issueCmd(bot, new AttackCmd(bot.name, targets, facingDir, mission), targets, mission);
        }
    }
    private void issueChargeCmd(MyBotInfo bot, Vector2 target, String mission) {
        if (bot.bot.getState() != BotInfo.STATE_CHARGING || !bot.mission.equals(mission)) {
            issueCmd(bot, new ChargeCmd(bot.name, target, mission), target, mission);
        }
    }

    private void issueChargeCmd(MyBotInfo bot, List<Vector2> targets, String mission) {
        if (bot.bot.getState() != BotInfo.STATE_CHARGING || !bot.mission.equals(mission)) {
            issueCmd(bot, new ChargeCmd(bot.name, targets, mission), targets, mission);
        }
    }
    private void issueMoveCmd(MyBotInfo bot, Vector2 target, String mission) {
        if (bot.bot.getState() != BotInfo.STATE_MOVING || !bot.mission.equals(mission)) {
            issueCmd(bot, new MoveCmd(bot.name, target, mission), target, mission);
        }
    }

    private void issueMoveCmd(MyBotInfo bot, List<Vector2> targets, String mission) {
        if (bot.bot.getState() != BotInfo.STATE_MOVING || !bot.mission.equals(mission)) {
            issueCmd(bot, new MoveCmd(bot.name, targets, mission), targets, mission);
        }
    }

    private void issueDefendCmd(MyBotInfo bot, Vector2 target, String mission) {
        if (bot.bot.getState() != BotInfo.STATE_DEFENDING || bot.target.distance(target) > 1) {
            Vector2 facingDirection = target.sub(bot.bot.getPosition());
            issueCmd(bot, new DefendCmd(bot.name, facingDirection, mission), target, mission);
        }
    }

    public void issueCmd(MyBotInfo bot, BotCommand cmd, Vector2 target, String mission) {
        List<Vector2> targets = new ArrayList<Vector2>();
        targets.add(target);
        issueCmd(bot, cmd, targets, mission);
    }

    public void issueCmd(MyBotInfo bot, BotCommand cmd, List<Vector2> targets, String mission) {
        float timeSinceLast = getTimeSinceLastCommand(bot);
        float minTime = 3.5f;
        if (bot.bot.getState() == BotInfo.STATE_HOLDING) {
            minTime = 1.5f;
        }
        if (timeSinceLast > minTime && !targets.isEmpty()) {
            Log.log(bot.name + " issues " + cmd.getCmdClass() + ", " + cmd.getDescription() + ", target: " + targets.get(targets.size()-1));
            if (targets.size() > 1) {
                Log.log(" waypoints: " + Utils.vector2ListToString(targets));
            }
            issue(cmd);
            bot.timeOfLastOrder = gameInfo.getMatchInfo().getTimePassed();
            bot.target = targets.get(targets.size()-1);
            bot.mission = mission;
            bot.realRole = bot.proposedRole;
            bot.targetPath = new ArrayList<Vector2>(targets);
            bot.pathOrigin = bot.bot.getPosition();
            issuedCmds.add(cmd);
        }
    }

    private List<Vector2> toVector2List(List<Tile> tiles, boolean addZigZag) {
        //checkTilesValidityForWalking(tiles);
        List<Vector2> path = new ArrayList<Vector2>(tiles.size());
        if (tiles.isEmpty()) {
            return path;
        }
        int skip = 2;
        //Tile prevTile = tiles.get(0);
        //int lastZigZagAddedIndex = -20;
        for (int i = 0; i < tiles.size() - skip; i += skip) {
            Tile t = tiles.get(i);
            Vector2 v = t.toVector2();
            addToList(path, v);
            /*if (addZigZag &&  (i-lastZigZagAddedIndex) > 4) {
              lastZigZagAddedIndex = i;
              double angle = Utils.getAngleTo(prevTile.x, prevTile.y, t.x, t.y);
              double turn1 = angle - Math.PI/4;
              int nr1 = t.calcValueOfAttackedTiles(turn1, levelInfo.getFovAngle(BotInfo.STATE_CHARGING), riskBasedCost);
              double turn2 = angle + Math.PI/4;
              int nr2 = t.calcValueOfAttackedTiles(turn2, levelInfo.getFovAngle(BotInfo.STATE_CHARGING), riskBasedCost);
              boolean added = false;
              if (nr1 > nr2) {
              Vector2 zig1V = v.add(Utils.facingAngleToVector2(turn1).scale(SMALL_DIST));//new Vector2(1.2f*(float)Math.cos(zigAngle), 1.2f*(float)Math.sin(zigAngle)));
              if (isWalkable(zig1V)) {
              addToList(vectors, zig1V);
              added = true;
              }
              }
              if (!added) {
              Vector2 zig2V = v.add(Utils.facingAngleToVector2(turn2).scale(SMALL_DIST));//new Vector2(1.2f*(float)Math.cos(zigAngle), 1.2f*(float)Math.sin(zigAngle)));
              if (isWalkable(zig2V)) {
              addToList(vectors, zig2V);
              }
              }
              i += 2;

            }
            prevTile = t;*/
        }
        if (addZigZag) {
            ZigZagger z = new ZigZagger();
            z.addZigZag(path, levelInfo.getFovAngle(BotInfo.STATE_CHARGING), riskBasedCostForFlagCarrier);
            // zigzag may result in non-walkable positions
            List<Vector2> walkablePath = new ArrayList<Vector2>();
            for (Vector2 v : path) {
                addToList(walkablePath, v);
            }
            path = walkablePath;
        }
        if (path.size() > 1) {
            path.remove(0);
        }
        addToList(path, tiles.get(tiles.size()-1).toVector2());
        return path;
    }

    private static Vector2[] lookAtToCheck = null;

    /**
     * Calculate a facing angle that will "look at" many risky squares
     * @param tiles
     * @return
     */
    private Vector2 calcOptimalLookAt(List<Tile> tiles) {
        if (lookAtToCheck == null) {
            int NR_X = 3;
            int NR_Y = 2;
            lookAtToCheck = new Vector2[NR_X*NR_Y];
            int index = 0;
            for (int i = 0; i < NR_X; ++i) {
                int x = (i*(width-1))/(NR_X-1);
                for (int j = 0; j < 2; ++j) {
                    int y = (j*(height-1))/(NR_Y-1);
                    lookAtToCheck[index] = Tile.get(x, y).toVector2();
                    ++index;
                }
            }
        }
        Vector2 bestPos = null;
        int bestV = calcValueOfLookAt(tiles, null);
        for (int i = 0; i < lookAtToCheck.length; ++i) {
            int val = calcValueOfLookAt(tiles, lookAtToCheck[i]);
            if (val > bestV) {
                bestV = val;
                bestPos = lookAtToCheck[i];
                System.err.println("tick " + nrTicks +", bestPos: " + lookAtToCheck[i]);
            }
        }
        return bestPos;
    }


    private int calcValueOfLookAt(List<Tile> tiles, Vector2 lookAt) {
        int value = 0;
        Tile prevTile = tiles.get(0);
        if (lookAt != null) {
            Tile lastTile = tiles.get(tiles.size()-1);
            double avgAngle = Utils.getAngleTo(prevTile.x, prevTile.y, lastTile.x, lastTile.y);
            double avgAngleToLookAt = Utils.getAngleTo(prevTile.x, prevTile.y, lookAt.getX(), lookAt.getY());
            double deltaAngle = Math.abs(avgAngle - avgAngleToLookAt);
            if (deltaAngle > Math.PI) {
                deltaAngle = 2*Math.PI - deltaAngle;
            }
            if (deltaAngle > Math.PI/3) {
                return -1;
            }
        }
        for (int i = 1; i < tiles.size(); ++i) {
            Tile t = tiles.get(i);
            double angle = 0;
            if (lookAt == null) {
                angle = Utils.getAngleTo(prevTile.x, prevTile.y, t.x, t.y);
            } else {
                angle = Utils.getAngleTo(t.x, t.y, lookAt.getX(), lookAt.getY());
            }
            value += t.calcValueOfAttackedTiles(angle, levelInfo.getFovAngle(BotInfo.STATE_CHARGING), visitedByEnemy);
            prevTile = t;
        }
        return value;
    }

    //------------------------------------------------------------------------------------------------------
    // OTHER
    //------------------------------------------------------------------------------------------------------

    private void handleCombatEvents() {
        for (MatchCombatEvent event : gameInfo.getMatchInfo().getCombatEvents()) {
            Log.log("Combat event " + Utils.combatEventToString(event.getType()) + ", subject: " + event.getSubject());
            switch (event.getType()) {
                case MatchCombatEvent.TYPE_RESPAWN: {
                                                        // all enemies are alive
                                                        for (MyBotInfo bot: enemyBots) {
                                                            bot.proposedRole = MyBotRole.OTHER;
                                                        }
                                                        nrLivingEnemies = nrBots;
                                                        break;
                } 
                case MatchCombatEvent.TYPE_BOT_KILLED: {
                                                           String botName = event.getSubject();
                                                           MyBotInfo myBot = allBots.get(botName);
                                                           myBot.proposedRole = myBot.realRole = MyBotRole.DEAD;
                                                           if (myBot.bot.getTeam().equals(enemyTeam)) {
                                                               --nrLivingEnemies;
                                                               ++nrEnemyTeamKilled;
                                                           } else {
                                                               ++nrMyTeamKilled;
                                                           }
                                                           break;
                }
                case MatchCombatEvent.TYPE_FLAG_PICKED_UP: {
                                                               FlagInfo flagInfo = gameInfo.getFlags().get(event.getSubject());
                                                               if (flagInfo.getTeam().equals(myTeam)) {
                                                                   setMyFlagStatus(FlagStatus.MOVING);
                                                               }
                                                               break;
                }
                case MatchCombatEvent.TYPE_FLAG_CAPTURED:
                case MatchCombatEvent.TYPE_FLAG_DROPPED:
                case MatchCombatEvent.TYPE_FLAG_RESTORED: {
                                                              FlagInfo flagInfo = gameInfo.getFlags().get(event.getSubject());
                                                              if (flagInfo.getTeam().equals(myTeam)) {
                                                                  setMyFlagStatus(FlagStatus.STANDING_STILL);
                                                              }
                                                              break;
                }
                default:
                                                          break;
            }
        }
    }

    private void setMyFlagStatus(FlagStatus status) {
        myFlagStatus = status;
        // immediately enable new commands defending commanders
        for (MyBotInfo bot : myBots) {
            if (bot.realRole == MyBotRole.DEFENDER || bot.realRole == MyBotRole.OTHER || bot.realRole == MyBotRole.FLAG_INTERCEPTOR) {
                bot.enableNewCommand();
            }
        }
    }

    private float getTimeSinceLastCommand(MyBotInfo bot) {
        return gameInfo.getMatchInfo().getTimePassed() - bot.timeOfLastOrder;
    }
    
    /*
     * Author: Inge Becht
     * Returns the distance of the nearest visible enemy.
     * 
     */
    private int DistanceNearestVisibleEnemy(BotInfo bot) {
        int nearestDist = 999999;
        for (String name : bot.getVisibleEnemies()) {
            BotInfo enemy = gameInfo.getBotInfo(name);
            if (enemy.getPosition() != null) {
                int dist = (int) bot.getPosition().distance(enemy.getPosition());
                if (dist < nearestDist) {  
                    nearestDist = dist;
                }
            }
        }
        return nearestDist;
    }
    

    private BotInfo findNearestVisibleEnemy(BotInfo bot) {
        float nearestDist = 1000000;
        BotInfo nearestBot = null;
        for (String name : bot.getVisibleEnemies()) {
            BotInfo enemy = gameInfo.getBotInfo(name);
            if (enemy.getPosition() != null) {
                float dist = bot.getPosition().distance(enemy.getPosition());
                if (dist < nearestDist) {
                    nearestBot = enemy;
                    nearestDist = dist;
                }
            }
        }
        return nearestBot;
    }


    private void findAmbushPoints() {
        float currTime = gameInfo.getMatchInfo().getTimePassed();
        if (currTime - timeOfLastAmbushCalculation < 4) {
            // ambush points don't change very much over time
            return;
        }
        timeOfLastAmbushCalculation = currTime;
        long start = System.currentTimeMillis();
        // calc distance to all points that are often visited by enemies
        int limit = Math.max(1, (2*totalNrVisitedByEnemy)/nrWalkableTiles);
        Distance visitedDist = new Distance(walkable, unitCost);
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                if (visitedByEnemy[i][j] >= limit) {
                    visitedDist.addInitial(i, j);
                }
            }
        }
        visitedDist.calcDistances(firingDistance - 3);
        // candidate ambush points are all points with low risk, close to points visited often by enemies
        List<AmbushPoint> candidatePoints = new ArrayList<AmbushPoint>();
        Tile enemyBotSpawn = Tile.get(enemyBotSpawnLocation);
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                Tile t = Tile.get(i, j);
                if (!isHighRisk(i, j) 
                		//TODO: Getvisibilitypercentaget to add as feature
                        && t.getVisibilityPercentage() < 40
                        && visitedDist.getDistance(i, j) < firingDistance
                        && shortestDistToEnemyFlagSpawnLocation.getDistance(i, j) > 5+firingDistance
                        && shortestDistToMyFlagSpawnLocation.getDistance(i, j) > 5+firingDistance
                        && enemyBotSpawn.distance.getDistance(i, j) > firingDistance) {
                    AmbushPoint point = new AmbushPoint();
                    point.tile = t;
                    candidatePoints.add(point);
                        }
            }
        }
        // determine value of each ambush point
        for (AmbushPoint point : candidatePoints) {
            Wheel wheel = new Wheel();
            wheel.setFovAngle(levelInfo.getFovAngle(BotInfo.STATE_DEFENDING));
            initWheelWithStrategicValues(point.tile, wheel, visitedByEnemy);
            point.bestFacingDirection = wheel.getBestFacingDirection();
            point.value = wheel.getBestValue();
        }
        // sort ambush points, take only the best
        Collections.sort(candidatePoints);
        ambushPoints.clear();
        for (int i = 0; i < 100 && i < candidatePoints.size(); ++i) {
            ambushPoints.add(candidatePoints.get(i));
        }
        // calculate the distance to the ambush points
        ambushDistance = new Distance(walkable, riskBasedCost);
        for (AmbushPoint p : ambushPoints) {
            ambushDistance.addInitial(p.tile.x, p.tile.y);
        }
        ambushDistance.calcDistances(400);
        Log.log("ambush calc time: " + (System.currentTimeMillis() - start));
        Log.log("Nr ambush points: " + candidatePoints.size());
        for (AmbushPoint p : ambushPoints) {
            Log.log(p.toString());
        }
    }

    private boolean canEngageInBattles(BotInfo bot) {
        if (bot.getHealth() <= 0) {
            return false;
        }
        int state = bot.getState();
        return state == BotInfo.STATE_ATTACKING || state == BotInfo.STATE_DEFENDING
            || state == BotInfo.STATE_SHOOTING || state == BotInfo.STATE_CHARGING;
    }

    private boolean isWalkable(Vector2 v) {
        return (v.getX() >= 0 && v.getX() < width && v.getY() >= 0 && v.getY() < height && Tile.get(v).isWalkable);
    }

    /**
     * Adjusts, if necessary, v to lie on a walkable place, at least characterRadius away from
     * any wall
     * @param v
     * @return
     */
    private Vector2 toWalkable(Vector2 v) {
        int x = (int)v.getX();
        int y = (int)v.getY();
        if (!Tile.isValid(x, y)) {
            x = Utils.clamp(0, width-1, x);
            y = Utils.clamp(0, height-1, y);
            v = Tile.get(x, y).toVector2();
        }
        Tile tile = Tile.get(v);
        if (!tile.isWalkable) {
            if (tile.node.neighbours.length > 0) {
                Tile neighbour = (Tile) tile.node.neighbours[0].to.object;
                return neighbour.toVector2();
            }
        }
        Vector2 result = v;
        double radius = levelInfo.getCharacterRadius();
        if (radius >= 0.45) {
            return result;
        }
        double fracX = v.getX() - Math.floor(v.getX());
        if (fracX <= radius) {
            Tile t = Tile.get(v);
            if (t.x <= 0 || !Tile.get(t.x-1, t.y).isWalkable) {
                result = new Vector2((float)(Math.floor(result.getX()) + radius + 0.01), result.getY());
            }
        } else if (fracX >= 1 - radius) {
            Tile t = Tile.get(v);
            if (t.x >= width-1 || !Tile.get(t.x+1, t.y).isWalkable) {
                result = new Vector2((float)(Math.floor(result.getX()) + 1 - radius - 0.01), result.getY());
            }
        }
        double fracY = v.getY() - Math.floor(v.getY());
        if (fracY <= radius) {
            Tile t = Tile.get(v);
            if (t.y <= 0 || !Tile.get(t.x, t.y-1).isWalkable) {
                result = new Vector2(result.getX(), (float)(Math.floor(result.getY()) + radius + 0.01));
            }
        } else if (fracY >= 1 - radius) {
            Tile t = Tile.get(v);
            if (t.y >= height-1 || !Tile.get(t.x, t.y+1).isWalkable) {
                result = new Vector2(result.getX(), (float)(Math.floor(result.getY()) + 1 - radius - 0.01));
            }
        }
        return result;
    }

    private void addToList(List<Vector2> list, Vector2 v) {
        if (isWalkable(v)) {
            list.add(toWalkable(v));
        } else {
            //System.err.println("Invalid path!");
            //Utils.reportError("Invalid tile on path; invalid vector: " + v);
        }
    }
    private void checkTilesValidityForWalking(List<Tile> path) {
        for (Tile t : path) {
            if (!Tile.isValid(t.x, t.y) || !t.isWalkable) {
                System.err.println("Invalid path!");
                for (Tile t2: path) {
                    System.err.print(" " + t2);
                }
                System.err.println();
                Utils.reportError("Invalid tile on path; invalid tile: " + t);
            }
        }
    }

    private void buildGraph() {
        long startTime = System.currentTimeMillis();
        graph = new Graph();
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                Tile t = Tile.get(i, j);
                if (t.isWalkable) {
                    graph.add(t.node);
                }
            }
        }
        graph.allNodesAdded();
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                Tile t = Tile.get(i, j);
                if (t.isWalkable) {
                    for (int a = -1; a <= 1; ++a) {
                        for (int b = -1; b <= 1; ++b) {
                            if ((a != 0 || b != 0) && Tile.isValid(t.x + a, t.y + b)) {
                                Tile neighbour = Tile.get(t.x + a, t.y + b);
                                if (neighbour.isWalkable) {
                                    boolean ok = true;
                                    if (a != 0 && b != 0) {
                                        ok = Tile.get(t.x + a, t.y).isWalkable || Tile.get(t.x, t.y + b).isWalkable;
                                    }
                                    if (ok) {
                                        Edge edge = new Edge(t.node, neighbour.node, 100);
                                        graph.add(edge);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        graph.allEdgesAdded();
        for (Node n : graph.nodes) {
            n.shuffleNeighbours();
        }
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                Tile t = Tile.get(i, j);
                t.distance = calcWalkDistance(t.toVector2(), unitCost);
            }
        }
        //graph.allPairsShortestPath();
        long duration = System.currentTimeMillis() - startTime;
        System.err.println("Graph building: " + duration);
    }

    /**
     * Called when the server sends the "shutdown" message to the commander. Use
     * this function to teardown your bot after the game is over.
     */
    @Override
    public void shutdown() {
        System.out.println("<shutdown> message received from server");
    }

    private EnemyBotInfo getEnemyBotInfo(String name) {
        for (EnemyBotInfo b : enemyLocations) {
            if (b.name.equals(name)) {
                return b;
            }
        }
        return null;
    }

    private void initWheelWithStrategicValues(Tile tile, Wheel wheel, int[][] value) {
        for (int i = 0; i < tile.attackedTiles.length; ++i) {
            Tile t = tile.attackedTiles[i];
            wheel.addValue(tile.attackAngles[i], value[t.x][t.y]);
        }
    }
    private void visitAttackedTiles(BotInfo bot, TileVisitor visitor) {
        if (bot.getFacingDirection() != null) {
            Tile botTile = Tile.get(bot.getPosition());
            double facingAngle = Utils.getFacingAngle(bot.getFacingDirection());
            botTile.visitAttackedTiles(facingAngle, levelInfo.getFovAngle(bot.getState()), visitor);
        }
    }

    private List<Tile> getTilesOnLine(Vector2 start, Vector2 end) {
        CollectingTileVisitor visitor = new CollectingTileVisitor();
        visitor.collectWalkableTiles(); 
        Tile.isVisible((int)start.getX(), (int)start.getY(), (int)end.getX(), (int)end.getY(), visitor);
        return visitor.visitedTiles;
    }


    private class CollectingTileVisitor implements TileVisitor {
        public List<Tile> visitedTiles = new ArrayList<Tile>();
        public int maxBlockHeight = 2;
        private boolean stopAtUnwalkable = true;

        public void collectWalkableTiles() {
            maxBlockHeight = 0;
            stopAtUnwalkable = false;
        }

        @Override
        public boolean visit(Tile t) {
            boolean ok = levelInfo.getBlockHeights()[t.x][t.y] <= maxBlockHeight;
            if (ok) {
                visitedTiles.add(t);
            }
            return ok || !stopAtUnwalkable;
        }

    }

    /**
     * Simulate a walk from enemy spawn to my flag + from enemy score to my flag
     * to initialize attackedByEnemy with reasonable values
     */
    private void prepareAttackedByEnemy() {
        List<Tile> path = shortestDistToMyFlagLocation.getPathFrom(Utils.getCenter(levelInfo.getBotSpawnAreas().get(enemyTeam)));
        updateAttackedByEnemy(path);
        path = shortestDistToMyFlagLocation.getPathFrom(levelInfo.getFlagScoreLocations().get(enemyTeam));
        updateAttackedByEnemy(path);
    }

    /**
     * An enemy has walked the given path; update information
     * @param path
     */
    private void updateAttackedByEnemy(List<Tile> path) {
        for (int i = 0; i < path.size()-1; ++i) {
            Tile from = path.get(i);
            Tile to = path.get(i+1);
            double angle = Utils.getAngleTo(from.x, from.y, to.x, to.y);
            from.visitAttackedTiles(angle, levelInfo.getFovAngle(BotInfo.STATE_ATTACKING), new UpdateAttackedTilesVisitor());
            ++visitedByEnemy[to.x][to.y];
            ++totalNrVisitedByEnemy;
        }
    }
    private class UpdateAttackedTilesVisitor implements TileVisitor {

        @Override
        public boolean visit(Tile t) {
            ++attackedByEnemy[t.x][t.y];
            ++totalNrAttackedByEnemy;
            return true;
        }
    }

    private class UpdateDefendedTilesVisitor implements TileVisitor {

        @Override
        public boolean visit(Tile t) {
            ++timesDefendedByEnemy[t.x][t.y];
            return true;
        }
    }

    //------------------------------------------------------------------------------------------------------
    // DEBUGGING
    //------------------------------------------------------------------------------------------------------

    private void logBoard() {
        if (nrTicks%500 == 0) {
            System.err.println("Tick " + nrTicks + ": Bot kills: " + nrEnemyTeamKilled + "-" + nrMyTeamKilled);
        }
        if (Log.logging || Log.buffering) {
            if (Log.logging) {
                logViewOfBoard();
            }
            Log.log("State of visible enemy bots: (nr living: " + nrLivingEnemies + ")");
            for (String name : visibleEnemies) {
                logBotInfo(gameInfo.getBotInfo(name));
            }
            Log.log("State of other enemy bots");
            for (BotInfo bot : gameInfo.getBots().values()) {
                if (bot.getTeam().equals(enemyTeam)) {
                    Log.log(bot.getName() + ", state: " + Utils.stateToString(bot.getState()) + ", health: " + bot.getHealth());
                }
            }
            Log.log("State of my bots: (nr living: " + nrLivingBots + ")");
            for (MyBotInfo myBot : myBots) {
                logBotInfo(myBot);
            }
            Log.log("My flag pos: " + Tile.get(myFlagLocation) + ", state: " + myFlagStatus + ", enemyFlag pos: " + Tile.get(enemyFlagLocation));
            if (Log.logging) {
                logViewOfAttacked();
            }
            Log.log("Bot kills: " + nrEnemyTeamKilled + "-" + nrMyTeamKilled);
        }
    }
    private void logViewOfBoard() {
        char[][] c = new char[width][height];
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                c[i][j] = '.';
            }
        }
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                if (levelInfo.getBlockHeights()[i][j] > 1) {
                    c[i][j] = '#';
                } else if (levelInfo.getBlockHeights()[i][j] > 0) {
                    c[i][j] = '=';
                }
            }
        }
        PrintVisibleTileVisitor visitor = new PrintVisibleTileVisitor();
        visitor.c = c;
        visitor.charToPrint = '2';
        for (String name : visibleEnemies) {
            BotInfo b = gameInfo.getBotInfo(name);
            visitAttackedTiles(b, visitor);
        }
        visitor.charToPrint = '1';
        for (MyBotInfo myBot : myBots) {
            try {
                visitAttackedTiles(myBot.bot, visitor);
            } catch (RuntimeException e) {
                Log.log("Exception for bot");
                logBotInfo(myBot);
                Log.log("Bot location: " + myBot.bot.getPosition());
                throw e;
            }
        }
        Tile flagTile = Tile.get(myFlagLocation);
        c[flagTile.x][flagTile.y] = 'F';
        Tile enemyFlagTile = Tile.get(enemyFlagLocation);
        c[enemyFlagTile.x][enemyFlagTile.y] = 'f'; 
        for (MyBotInfo bot : myBots) {
            Tile t = Tile.get(bot.bot.getPosition());
            c[t.x][t.y] = 'M'; 
        }
        for (String name : visibleEnemies) {
            BotInfo b = gameInfo.getBotInfo(name);
            Tile t = Tile.get(b.getPosition());
            c[t.x][t.y] = 'e'; 
        }
        for (int row = 0; row < height; ++row) {
            StringBuilder b = new StringBuilder();
            b.append(String.format("%02d ", row));
            for (int col = 0; col < width; ++ col) {
                b.append(c[col][row]);
            }
            Log.log(b.toString());
        }
    }

    private void logViewOfAttacked() {
        char[][] c = new char[width][height];
        double averageAttacksPerSquare = ((double)totalNrAttackedByEnemy)/nrWalkableTiles;
        if (averageAttacksPerSquare < 0.0001) {
            averageAttacksPerSquare = 1;
        }
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                if (walkable[i][j] && c[i][j] != 'f' && c[i][j] != 'F') {
                    int attackRisk = (int)(attackedByEnemy[i][j] / averageAttacksPerSquare);
                    c[i][j] = (char)('0' + attackRisk);
                }
            }
        }
        for (int row = 0; row < height; ++row) {
            StringBuilder b = new StringBuilder();
            b.append(String.format("%02d ", row));
            for (int col = 0; col < width; ++ col) {
                b.append(c[col][row]);
            }
            Log.log(b.toString());
        }
    }
    private void logBotInfo(MyBotInfo bot) {
        Log.log(bot.name + ", proposed: " + bot.proposedRole + ", mission: " + bot.realRole + "/" + bot.mission + ", target: " + bot.target
                + ", state: " + Utils.stateToString(bot.bot.getState()) + ", pos: " + Tile.get(bot.bot.getPosition())
                + ", facDir: " + bot.bot.getFacingDirection() + ",  hasFlag: " + bot.bot.getFlag() + ", health: " + bot.bot.getHealth());
        printVisibleEnemies(bot.bot);
    }

    private void logBotInfo(BotInfo bot) {
        Log.log(bot.getName() + ", state: " + Utils.stateToString(bot.getState()) +", pos: " + Tile.get(bot.getPosition()) + ", facDir: " + bot.getFacingDirection() 
                + ", hasFlag: " + bot.getFlag() + ", health: " + bot.getHealth());
        printVisibleEnemies(bot);
    }

    private void printVisibleEnemies(BotInfo bot) {
        if (!bot.getVisibleEnemies().isEmpty()) {
            StringBuilder buf = new StringBuilder();
            for (String name : bot.getVisibleEnemies()) {
                buf.append(name);
                buf.append(" ");
            }
            Log.log("  visible enemies: " + buf);
        }
    }

    public GameInfo getGameInfo() {
        return gameInfo;
    }

    public LevelInfo getLevelInfo() {
        return levelInfo;
    }

    private class PrintVisibleTileVisitor implements TileVisitor {
        char[][] c;
        char charToPrint;

        @Override
        public boolean visit(Tile t) {
            if (c[t.x][t.y] == '.' || c[t.x][t.y] == charToPrint) {
                c[t.x][t.y] = charToPrint;
            } else {
                c[t.x][t.y] = '3';
            }
            return true;
        }

    }
}


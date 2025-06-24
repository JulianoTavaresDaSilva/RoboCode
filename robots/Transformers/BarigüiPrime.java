package Transformers;

import robocode.*;
import robocode.util.Utils;
import java.awt.geom.*;
import java.util.ArrayList;

public class BarigüiPrime extends AdvancedRobot {

    private Point2D.Double myLocation;       
    private Point2D.Double enemyLocation;   
    private Rectangle2D.Double fieldRect;   

    private ArrayList<EnemyWave> enemyWaves = new ArrayList<>();
    private ArrayList<Integer> surfDirections = new ArrayList<>(); 
    private ArrayList<Double> surfAbsBearings = new ArrayList<>();
    private static final int BINS = 47;
    private static final double[] surfStats = new double[BINS]; 
    private static final double WALL_STICK = 160;

    private double opponentEnergy = 100; 
    public void run() {
        fieldRect = new Rectangle2D.Double(18, 18, getBattleFieldWidth() - 36, getBattleFieldHeight() - 36);

        enemyWaves = new ArrayList<>();
        surfDirections = new ArrayList<>();
        surfAbsBearings = new ArrayList<>();
		
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
		
        while (true) {
            turnRadarRightRadians(Double.POSITIVE_INFINITY);
        }
    }
    public void onScannedRobot(ScannedRobotEvent e) {
        fire(5);
    }
    public void onHitByBullet(HitByBulletEvent e) {
        back(10);
    }

    public void onHitWall(HitWallEvent e) {
        back(20);
    }


}
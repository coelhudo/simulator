package simulator.system;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import simulator.Environment;
import simulator.SLAViolationLogger;
import simulator.Violation;
import simulator.am.ComputeSystemAM;
import simulator.jobs.BatchJob;
import simulator.physical.BladeServer;
import simulator.physical.DataCenter;
import simulator.ra.MHR;
import simulator.schedulers.LeastRemainFirstScheduler;
import simulator.schedulers.Scheduler;

public class ComputeSystem extends GeneralSystem {

    private static final Logger LOGGER = Logger.getLogger(ComputeSystem.class.getName());
    
    private Violation SLAViolationType; // different type of violation:
    // ComputeNodeShortage, DEADLINEPASSED
    private List<BatchJob> waitingList;
    private int totalJob = 0;
    private double inputTime;
    private boolean blocked = false;
    private int priority;
    private Environment environment;
    private SLAViolationLogger slaViolationLogger;
    private DataCenter dataCenter;

    private ComputeSystem(SystemPOD systemPOD, Environment environment, DataCenter dataCenter, SLAViolationLogger slaViolationLogger) {
        super(systemPOD);
        this.environment = environment;
        this.dataCenter = dataCenter;
        this.slaViolationLogger = slaViolationLogger;
        setComputeNodeList(new ArrayList<BladeServer>());
        waitingList = new ArrayList<BatchJob>();
        setComputeNodeIndex(new ArrayList<Integer>());
        setScheduler(new LeastRemainFirstScheduler());
        setBis(systemPOD.getBis());
        setNumberOfNode(systemPOD.getNumberOfNode());
        priority = ((ComputeSystemPOD) systemPOD).getPriority();
        setRackIDs(systemPOD.getRackIDs());
        // placement=new jobPlacement(ComputeNodeList);
        setResourceAllocation(new MHR(this.environment, this.dataCenter));
        totalJob = 0;
    }

    boolean runAcycle() {
        setSLAviolation(0);
        int numberOfFinishedJob = 0;
        // if(Main.localTime%1200==0 |Main.localTime%1200==2 )
        // ASP();
        BatchJob j = new BatchJob(environment, dataCenter);
        // reads all jobs with arrival time less than Localtime
        while (readJob(j)) {
            if (inputTime > environment.getCurrentLocalTime()) {
                break;
            }
            j = new BatchJob(environment, dataCenter);
        }
        if (!isBlocked()) {
            // feeds jobs from waiting list to servers as much as possible
            getFromWaitinglist();
            for (int temp = 0; temp < getComputeNodeList().size(); temp++) {
                getComputeNodeList().get(temp).run(new BatchJob(environment, dataCenter));
            }
            for (int temp = 0; temp < getComputeNodeList().size(); temp++) {
                numberOfFinishedJob = getComputeNodeList().get(temp).getTotalFinishedJob() + numberOfFinishedJob;
            }
            //LOGGER.info("total "+totalJob+ "\t finished Job= "+numberOfFinishedJob+"\t LocalTime="+Main.localTime);
        }
        // if is blocked and was not belocked before make it blocked
        if (isBlocked() && !allNodesAreBlocked()) {
            makeSystemaBlocked();
        }

        if (!isBlocked()) {
            getAM().monitor();
            getAM().analysis(0);
        }
        // LOGGER.info(Main.localTime +"\t"+totalJob+
        // "\t"+numberOfFinishedJob);
        if (numberOfFinishedJob == totalJob) {
            markAsDone();
            return true;
        } else {
            return false;
        }
    }
    /// returns true if all nodes are blocked

    boolean allNodesAreBlocked() {
        for (BladeServer bladeServer : getComputeNodeList()) {
            if (bladeServer.getReady() != -1) {
                return false;
            }
        }
        return true;
    }

    void makeSystemaBlocked() {
        for (BladeServer bladeServer : getComputeNodeList()) {
            bladeServer.setBackUpReady(bladeServer.getReady());
            bladeServer.setReady(-1);
        }
    }

    public void makeSystemaUnBlocked() {
        for (BladeServer bladeServer: getComputeNodeList()) {
            bladeServer.setReady(bladeServer.getBackUpReady());
        }
    }

    int getFromWaitinglist() {
        setSLAviolation(Violation.NOTHING);
        if (waitingList.isEmpty()) {
            return 0;
        }
        BatchJob job = (BatchJob) (getScheduler().nextJob(waitingList));
        while (job.getStartTime() <= environment.getCurrentLocalTime()) {
            int[] indexes = new int[job.getNumOfNode()]; // number of node the
            // last job wants
            int[] listServer = new int[job.getNumOfNode()];
            if (getResourceAllocation().allocateSystemLevelServer(getComputeNodeList(), indexes)[0] == -2) {
                setSLAviolation(Violation.COMPUTE_NODE_SHORTAGE);
                // LOGGER.info("COMPUTE NODE SHORTAGE in
                // getFromWaitingList");
                return 0; // can not find the bunch of requested node for the
                // job
            }
            listServer = makeListofServer(indexes);
            for (int i = 0; i < indexes.length; i++) {

                job.setListOfServer(listServer);
                getComputeNodeList().get(indexes[i]).feedWork(job);// feed also
                // takes care
                // of setting
                // ready :)
                if (indexes.length > 1) {
                    getComputeNodeList().get(indexes[i]).setDependency(1); // means:
                    // this
                    // server
                    // has
                    // a
                    // process
                    // which
                    // is
                    // dependent
                    // on
                    // others
                } else {
                    getComputeNodeList().get(indexes[i]).setDependency(0);
                }
            }
            // Check if dealine is missed
            if (environment.getCurrentLocalTime() - job.getStartTime() > job.getDeadline()) {
                setSLAviolation(Violation.DEADLINEPASSED);
                // LOGGER.info("DEADLINE PASSED in getFromWaitingList");
            }
            ////////////////////////////
            waitingList.remove(job);
            if (waitingList.isEmpty()) {
                return 0;
            }
            job = (BatchJob) (getScheduler().nextJob(waitingList));
        }
        return 0; // it is not important
    }

    int[] makeListofServer(int[] list) {
        int[] retList = new int[list.length];
        // int
        // NumOfSerInChas=DataCenter.theDataCenter.chassisSet.get(0).servers.size();
        // map the index in CS compute node list to physical index(chassID ,
        // ServerID)
        for (int i = 0; i < list.length; i++) {
            retList[i] = getComputeNodeList().get(list[i]).getServerID();// chassisID*NumOfSerInChas+ComputeNodeList.get(list[i]).serverID;
        }
        return retList;
    }

    void setSLAviolation(Violation flag) {
        SLAViolationType = Violation.NOTHING;
        if (flag == Violation.COMPUTE_NODE_SHORTAGE) // means there is not
        // enough compute nodes for
        // job, this function is
        // called from
        // resourceIsAvailable
        {
            SLAViolationType = Violation.COMPUTE_NODE_SHORTAGE;
            SLAviolation++;
        }
        if (flag == Violation.DEADLINEPASSED) {
            SLAViolationType = Violation.DEADLINEPASSED;
            SLAviolation++;
        }
        if (SLAViolationType != Violation.NOTHING) {
            slaViolationLogger.logHPCViolation(getName(), SLAViolationType);
            setAccumolatedViolation(getAccumolatedViolation() + 1);
        }
    }

    boolean readJob(BatchJob j) {
        try {
            String line = getBis().readLine();
            if (line == null) {
                return false;
            }
            line = line.replace("\t", " ");
            String[] numbers = line.split(" ");
            if (numbers.length < 5) {
                return false;
            }
            // Input log format: (time, requiertime, CPU utilization, number of
            // core, dealine for getting to a server buffer)
            inputTime = Double.parseDouble(numbers[0]);
            j.setRemainParam(Double.parseDouble(numbers[1]), Double.parseDouble(numbers[2]),
                    Integer.parseInt(numbers[3]), Integer.parseInt(numbers[4]));
            j.setStartTime(inputTime);
            boolean add = waitingList.add(j);
            // number of jobs which are copied on # of requested nodes
            totalJob = totalJob + 1 /* +Integer.parseInt(numbers[3]) */;
            // LOGGER.info("Readed inputTime= " + inputTime + " Job
            // Reqested Time=" + j.startTime+" Total job so far="+ total);
            return add;
        } catch (IOException ex) {
            LOGGER.info("readJOB EXC readJOB false ");
            Logger.getLogger(Scheduler.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    
    List<Integer> getindexSet() {
        return getComputeNodeIndex();
    }

    public int numberofRunningNode() {
        int cnt = 0;
        for (int i = 0; i < getComputeNodeList().size(); i++) {
            if (getComputeNodeList().get(i).getReady() > -1) {
                cnt++;
            }
        }
        return cnt;
    }

    public int numberofIdleNode() {
        int cnt = 0;
        for (int i = 0; i < getComputeNodeList().size(); i++) {
            if (getComputeNodeList().get(i).getReady() == -1) {
                cnt++;
            }
        }
        return cnt;
    }

    public void activeOneNode() {
        int i = 0;
        for (i = 0; i < getComputeNodeList().size(); i++) {
            if (getComputeNodeList().get(i).getReady() == -1) {
                getComputeNodeList().get(i).restart();
                getComputeNodeList().get(i).setReady(1);
                break;
            }
        }
        LOGGER.info("activeone node in compuet system MIIIIPPPSSS    " + getComputeNodeList().get(i).getMips());
    }

    double finalized() {
        try {
            getBis().close();
        } catch (IOException ex) {
            Logger.getLogger(EnterpriseApp.class.getName()).log(Level.SEVERE, null, ex);
        }
        double totalResponsetime = 0;
        for (BladeServer bladeServer : getComputeNodeList()) {
            totalResponsetime = totalResponsetime + bladeServer.getResponseTime();

        }
        return totalResponsetime;
    }

    public static ComputeSystem Create(SystemPOD systemPOD, Environment environment, DataCenter dataCenter, SLAViolationLogger slaViolationLogger) {
        ComputeSystem computeSystem = new ComputeSystem(systemPOD, environment, dataCenter, slaViolationLogger);
        computeSystem.getResourceAllocation().initialResourceAloc(computeSystem);
        computeSystem.setAM(new ComputeSystemAM(computeSystem, environment));
        return computeSystem;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }
}

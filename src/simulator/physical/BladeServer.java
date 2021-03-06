//Ready in web based workload is not set correctly, check it out!
package simulator.physical;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import simulator.Environment;
import simulator.ResponseTime;
import simulator.jobs.BatchJob;
import simulator.jobs.EnterpriseJob;
import simulator.jobs.InteractiveJob;

public class BladeServer {

    private static final Logger LOGGER = Logger.getLogger(BladeServer.class.getName());

    private List<ResponseTime> responseList;
    private List<ResponseTime> responseListWeb;
    private int dependency = 0;
    private double[] frequencyLevel;
    private double[] powerBusy;
    private double[] powerIdle;
    private double Mips;
    private double idleConsumption;
    private String bladeType;
    private double respTime = 0;
    private double resTimeEpoch = 0;
    private double currentCPU = 0;
    private double queueLength;
    private double totalJob = 0;
    private double totalJobEpoch = 0;
    private int ready;
    private int backUpReady;
    private List<BatchJob> activeBatchList;
    private List<BatchJob> blockedBatchList;
    private List<EnterpriseJob> EnterprizList;
    private List<InteractiveJob> WebBasedList;
    private int chassisID;
    private int totalFinishedJob = 0;
    private int serverID;
    private int rackId;
    // Application Bundle
    private int timeTreshold = 0;
    private int SLAPercentage;
    // WorkLoad Bundle
    private int maxExpectedRes = 0;
    private boolean SLAviolation;
    private Environment environment;

    public BladeServer(int chasID, Environment environment) {
        this.environment = environment;
        setRespTime(0);
        // if it is -1 means that it is not put in the proper position yet ID
        // should be set
        setChassisID(chasID);
        setBladeType(new String());
        setCurrentCPU(0);
        setActiveBatchList(new ArrayList<BatchJob>());
        setBlockedBatchList(new ArrayList<BatchJob>());
        setEnterprizList(new ArrayList<EnterpriseJob>());
        setWebBasedList(new ArrayList<InteractiveJob>());
        setResponseList(new ArrayList<ResponseTime>());
        setResponseListWeb(new ArrayList<ResponseTime>());
        setQueueLength(0);
        // -3 means it is not assigned to any system yet
        // -2: it is in a system but is not assigned to an application
        // -1 idle
        // 0 or 1 is ready or not just the matter of CPU utilization over 100%
        // or not
        setReady(-3);
        setTotalFinishedJob(0);
        setSLAviolation(false);
        setMips(1.4);
    }
    // Transaction system

    public void configSLAparameter(int time, int percentage) {
        setTimeTreshold(time);
        setSLAPercentage(percentage);
    }
    // Interactive System

    public void configSLAparameter(int time) {
        setMaxExpectedRes(time);
    }

    public double[] getPwrParam() {
        double[] ret = new double[3];
        int i = getCurrentFreqLevel();
        ret[0] = getPowerBusy()[i];
        ret[1] = getPowerIdle()[i];
        ret[2] = getIdleConsumption();
        return ret;
    }

    public double getPower() {
        double pw = 0, w = 0, a = 0, cpu = 0, mips = 0;
        int j;
        cpu = getCurrentCPU();
        // if(cpu!=0)
        // LOGGER.info(cpu +" \ttime ="+Main.localTime +" \t chassi
        // id="+chassisID );
        // if(servers.get(i).currentCPU==100) LOGGER.info(chassisID);
        mips = getMips();
        if (mips == 0) {
            pw = pw + getIdleConsumption();
            LOGGER.info("MIPS Zero!!!!");
        } else {
            for (j = 0; j < 3; j++) {
                if (getFrequencyLevel()[j] == mips) {
                    break;
                }
            }
            w = getPowerIdle()[j];
            a = getPowerBusy()[j] - w;
            if (getReady() == -1 | getReady() == -2 | getReady() == -3) {
                // if the server is in idle state

                a = 0;
                w = getIdleConsumption();
                // LOGGER.info(Main.localTime);
            }
            pw = pw + a * cpu / 100 + w;

        }
        return pw;
    }

    public void restart() {
        setRespTime(0);
        setCurrentCPU(0);
        setActiveBatchList(new ArrayList<BatchJob>());
        setBlockedBatchList(new ArrayList<BatchJob>());
        setEnterprizList(new ArrayList<EnterpriseJob>());
        setWebBasedList(new ArrayList<InteractiveJob>());
        setQueueLength(0);
        ///// check
        setReady(-1);
        setTotalFinishedJob(0);
        setMips(1.4);
        setResTimeEpoch(0);
        setTotalJob(0);
        setTotalJobEpoch(0);
        setSLAviolation(false);
    }
    // if it blongs to Enterprise system

    public void makeItIdle(EnterpriseJob jj) {
        // System.out.print("\tIdle\t\t\t\t\t@:"+Main.localTime);
        setReady(-1);
        setMips(getFrequencyLevel()[0]);
    }

    public void feedWork(InteractiveJob j) {
        double nums = j.getNumberOfJob();
        int time = j.getArrivalTimeOfJob();
        setQueueLength(getQueueLength() + nums);
        InteractiveJob wJob = new InteractiveJob();
        wJob.setArrivalTimeOfJob(time);
        wJob.setNumberOfJob(nums);
        getWebBasedList().add(wJob);
        setTotalJob(getTotalJob() + nums);
    }
    // feeding batch type Job to blade server

    public void feedWork(BatchJob job) {
        getActiveBatchList().add(job);
        setReady();
        setDependency();
        setTotalJob(getTotalJob() + 1);
    }
    // feeding webbased type Job to blade server

    public void feedWork(EnterpriseJob j) {
        double nums = j.getNumberOfJob();
        int time = j.getArrivalTimeOfJob();
        setQueueLength(getQueueLength() + nums);
        EnterpriseJob wJob = new EnterpriseJob();
        wJob.setArrivalTimeOfJob(time);
        wJob.setNumberOfJob(nums);
        getEnterprizList().add(wJob);
        setTotalJob(nums + getTotalJob());
    }

    public int getCurrentFreqLevel() {
        for (int i = 0; i < getFrequencyLevel().length; i++) {
            if (getMips() == getFrequencyLevel()[i]) {
                return i; // statrs from 1 not zero!
            }
        }
        LOGGER.info("wrong frequency level !! ");
        return -1;
    }

    public int increaseFrequency() {
        // LOGGER.info("MIIIPPSSS "+Mips);
        if (getCurrentFreqLevel() == 2) {
            return 0;
        } else {
            setMips(getFrequencyLevel()[getCurrentFreqLevel() + 1]); // getCurrentFrequency
            // already
            // increased
            // the
            // freq
            // level
            environment.updateNumberOfMessagesFromDataCenterToSystem();
        }
        if (getMips() == 0) {
            LOGGER.info("Mipss sefr shoodd!!!");
        }
        return 1;
    }

    public int decreaseFrequency() {
        // LOGGER.info("Decreasing frequency");
        if (getCurrentFreqLevel() == 0) {// LOGGER.info("Minimum
            // Frequency Level ~~~ ");
            return 0;
        } else {
            setMips(getFrequencyLevel()[getCurrentFreqLevel() - 1]);
            environment.updateNumberOfMessagesFromDataCenterToSystem();
        }
        if (getMips() == 0) {
            LOGGER.info("Mipss sefr shoodd!!!");
        }
        return 1;
    }
    // running batch type JOB

    public int run(BatchJob j) {
        double tempCpu = 0;
        double num = getActiveBatchList().size(), index = 0, index_1 = 0, rmpart = 0;
        int i = 0;
        double share = 0, share_t = 0, extraShare = 0;
        if (num == 0) {
            setReady(1);
            setDependency();
            setCurrentCPU(0);
            return 0;
        }
        share = getMips() / num;// second freqcuency level!
        // LOGGER.info("Share "+share);
        share_t = share;
        int ret_done = 0;
        while (index < num) { // index<activeBatchList.size()
            index_1 = index;
            for (i = 0; i < getActiveBatchList().size(); i++) {
                if (getActiveBatchList().get(i).getUtilization() <= share
                        & getActiveBatchList().get(i).getIsChangedThisTime() == 0) {
                    extraShare = extraShare + share - getActiveBatchList().get(i).getUtilization();
                    index++;
                    getActiveBatchList().get(i).setIsChangedThisTime(1);
                    tempCpu = getActiveBatchList().get(i).getUtilization() + tempCpu;
                    ret_done = done(i, share_t);
                    i = i - ret_done;
                    // i=i-done(i,activeBatchList.get(i).utilization);
                }
            }
            for (i = 0; i < getActiveBatchList().size(); i++) {
                if (getActiveBatchList().get(i).getIsChangedThisTime() == 0) {
                    rmpart++;
                }
            }
            if (rmpart != 0) {
                share = share + extraShare / rmpart;
            }
            rmpart = 0;
            extraShare = 0;
            if (index == index_1) {
                break;
            }
        }
        for (i = 0; i < getActiveBatchList().size(); i++) {
            if (getActiveBatchList().get(i).getIsChangedThisTime() == 0) {
                // ret_done=done(i,share/activeBatchList.get(i).utilization);
                if ((share / getActiveBatchList().get(i).getUtilization()) > 1) {
                    LOGGER.info("share more than one!\t" + share_t + "\t" + share + "\t"
                            + getActiveBatchList().get(i).getUtilization() + "\t" + environment.getCurrentLocalTime());
                }
                getActiveBatchList().get(i).setIsChangedThisTime(1);
                ret_done = done(i, share / getActiveBatchList().get(i).getUtilization());
                tempCpu = tempCpu + share;
                i = i - ret_done; // if a job has been removed (finished) in
                // DONE function
            }
        }
        for (BatchJob job : getActiveBatchList()) {
            job.setIsChangedThisTime(0);
        }
        // Inja be nazaram /MIPS ham mikhad ke sad beshe fek konam MIPS ro dar
        // nazar nagereftam!
        setCurrentCPU(100.0 * tempCpu / getMips());
        // LOGGER.info("CPU= " + currentCPU +"num= "+num);
        setReady();
        setDependency();
        return 1;
    }

    public int done(int tmp, double share) {
        // return 1 means: a job has been finished
        BatchJob job = getActiveBatchList().get(tmp);
        int ki;
        // int
        // serverIndex=chassisID*DataCenter.theDataCenter.chassisSet.get(0).servers.size()+serverID;
        // //getting this server ID
        int serverIndex = getServerID();
        ki = job.getThisNodeIndex(serverIndex);
        if (share == 0) {
            LOGGER.info(
                    "In DONE share== zero00000000000000000000000000000000000000oo,revise the code  need some work!");
            job.setExitTime(environment.getCurrentLocalTime());
            getActiveBatchList().remove(tmp--);
            // totalFinishedJob++;
            return 1;
        }
        if (ki == -1) {
            LOGGER.info("Blade server is wrong in BladeServer!!!");
        }
        // setRemainAllNodes(tmp, share);
        job.getRemain()[ki] = job.getRemain()[ki] - share;
        if (job.getRemain()[ki] <= 0) {
            getBlockedBatchList().add(job);
            getActiveBatchList().get(tmp).setIsChangedThisTime(0);
            getActiveBatchList().remove(job);// still exsits in other nodes
            if (job.allDone()) {

                job.jobFinished();

                setDependency();
                setTotalFinishedJob(getTotalFinishedJob() + 1);
                return 1;
            }
        }
        return 0;
    }

    void setDependency() {
        if (!getBlockedBatchList().isEmpty()) {
            dependency = 1;
            return;
        }
        dependency = 0;
    }

    public void setReady() {
        double tmp = 0, treshold = 1;
        int num = 0, i;
        num = getActiveBatchList().size();
        for (i = 0; i < num; i++) {
            tmp = tmp + getActiveBatchList().get(i).getUtilization();
        }
        if (tmp >= treshold) {
            ready = 0;
        } else {
            ready = 1;
        }
    }

    void readFromNode(Node node) {
        NodeList childNodes = node.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            if (childNodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                // if(childNodes.item(i).getNodeName().equalsIgnoreCase("ID"))
                // {
                // serverID =
                // Integer.parseInt(childNodes.item(i).getChildNodes().item(0).getNodeValue().trim());
                // }
                if (childNodes.item(i).getNodeName().equalsIgnoreCase("BladeType")) {
                    setBladeType(childNodes.item(i).getChildNodes().item(0).getNodeValue().trim());
                }
                if (childNodes.item(i).getNodeName().equalsIgnoreCase("MIPS")) {
                    String str = childNodes.item(i).getChildNodes().item(0).getNodeValue().trim();
                    String[] split = str.split(" ");
                    setFrequencyLevel(new double[split.length]);
                    for (int j = 0; j < split.length; j++) {
                        getFrequencyLevel()[j] = Double.parseDouble(split[j]);
                    }
                }
                if (childNodes.item(i).getNodeName().equalsIgnoreCase("FullyLoaded")) {
                    String str = childNodes.item(i).getChildNodes().item(0).getNodeValue().trim();
                    String[] split = str.split(" ");
                    setPowerBusy(new double[split.length]);
                    for (int j = 0; j < split.length; j++) {
                        getPowerBusy()[j] = Double.parseDouble(split[j]);
                    }
                }
                if (childNodes.item(i).getNodeName().equalsIgnoreCase("Idle")) {
                    String str = childNodes.item(i).getChildNodes().item(0).getNodeValue().trim();
                    String[] split = str.split(" ");
                    setPowerIdle(new double[split.length]);
                    for (int j = 0; j < split.length; j++) {
                        getPowerIdle()[j] = Double.parseDouble(split[j]);
                    }
                }
                if (childNodes.item(i).getNodeName().equalsIgnoreCase("Standby")) {
                    setIdleConsumption(
                            Double.parseDouble(childNodes.item(i).getChildNodes().item(0).getNodeValue().trim()));

                }
            }
        }
    }
    // void addToresponseArray(double num,int time)
    // {
    // responseTime t= new responseTime();
    // t.numberOfJob=num;
    // t.responseTime=time;
    // responseList.add(t);
    // }
    // void addToresponseArrayWeb(double num,int time)
    // {
    // if(time>maxExpectedRes)
    // SLAviolation=true;
    // responseTime t= new responseTime();
    // t.numberOfJob=num;
    // t.responseTime=time;
    // responseList.add(t);
    // }
    // int whichServer(int i)
    // {
    // return i%DataCenter.theDataCenter.chassisSet.get(0).servers.size();
    // }
    // int whichChasiss (int i)
    // {
    // return i/DataCenter.theDataCenter.chassisSet.get(0).servers.size();
    // }

    public List<ResponseTime> getResponseList() {
        return responseList;
    }

    public void setResponseList(List<ResponseTime> responseList) {
        this.responseList = responseList;
    }

    public List<ResponseTime> getResponseListWeb() {
        return responseListWeb;
    }

    public void setResponseListWeb(List<ResponseTime> responseListWeb) {
        this.responseListWeb = responseListWeb;
    }

    public int getDependency() {
        return dependency;
    }

    public void setDependency(int dependency) {
        this.dependency = dependency;
    }

    public double[] getFrequencyLevel() {
        return frequencyLevel;
    }

    public void setFrequencyLevel(double[] frequencyLevel) {
        this.frequencyLevel = frequencyLevel;
    }

    public double[] getPowerBusy() {
        return powerBusy;
    }

    public void setPowerBusy(double[] powerBusy) {
        this.powerBusy = powerBusy;
    }

    public double[] getPowerIdle() {
        return powerIdle;
    }

    public void setPowerIdle(double[] powerIdle) {
        this.powerIdle = powerIdle;
    }

    public double getIdleConsumption() {
        return idleConsumption;
    }

    public void setIdleConsumption(double idleConsumption) {
        this.idleConsumption = idleConsumption;
    }

    public double getMips() {
        return Mips;
    }

    public void setMips(double mips) {
        Mips = mips;
    }

    public double getResponseTime() {
        return respTime;
    }

    public void setRespTime(double respTime) {
        this.respTime = respTime;
    }

    public double getResTimeEpoch() {
        return resTimeEpoch;
    }

    public void setResTimeEpoch(double resTimeEpoch) {
        this.resTimeEpoch = resTimeEpoch;
    }

    public double getCurrentCPU() {
        return currentCPU;
    }

    public void setCurrentCPU(double currentCPU) {
        this.currentCPU = currentCPU;
    }

    public double getQueueLength() {
        return queueLength;
    }

    public void setQueueLength(double queueLength) {
        this.queueLength = queueLength;
    }

    public double getTotalJob() {
        return totalJob;
    }

    public void setTotalJob(double totalJob) {
        this.totalJob = totalJob;
    }

    public double getTotalJobEpoch() {
        return totalJobEpoch;
    }

    public void setTotalJobEpoch(double totalJobEpoch) {
        this.totalJobEpoch = totalJobEpoch;
    }

    public int getReady() {
        return ready;
    }

    public void setReady(int ready) {
        this.ready = ready;
    }

    public int getBackUpReady() {
        return backUpReady;
    }

    public void setBackUpReady(int backUpReady) {
        this.backUpReady = backUpReady;
    }

    public List<BatchJob> getActiveBatchList() {
        return activeBatchList;
    }

    public void setActiveBatchList(List<BatchJob> activeBatchList) {
        this.activeBatchList = activeBatchList;
    }

    public List<BatchJob> getBlockedBatchList() {
        return blockedBatchList;
    }

    public void setBlockedBatchList(List<BatchJob> blockedBatchList) {
        this.blockedBatchList = blockedBatchList;
    }

    public List<EnterpriseJob> getEnterprizList() {
        return EnterprizList;
    }

    public void setEnterprizList(List<EnterpriseJob> enterprizList) {
        EnterprizList = enterprizList;
    }

    public List<InteractiveJob> getWebBasedList() {
        return WebBasedList;
    }

    public void setWebBasedList(List<InteractiveJob> webBasedList) {
        WebBasedList = webBasedList;
    }

    public int getTotalFinishedJob() {
        return totalFinishedJob;
    }

    public void setTotalFinishedJob(int totalFinishedJob) {
        this.totalFinishedJob = totalFinishedJob;
    }

    public int getChassisID() {
        return chassisID;
    }

    public void setChassisID(int chassisID) {
        this.chassisID = chassisID;
    }

    public int getServerID() {
        return serverID;
    }

    public void setServerID(int serverID) {
        this.serverID = serverID;
    }

    public int getRackId() {
        return rackId;
    }

    public void setRackId(int rackId) {
        this.rackId = rackId;
    }

    public int getTimeTreshold() {
        return timeTreshold;
    }

    public void setTimeTreshold(int timeTreshold) {
        this.timeTreshold = timeTreshold;
    }

    public int getSLAPercentage() {
        return SLAPercentage;
    }

    public void setSLAPercentage(int sLAPercentage) {
        SLAPercentage = sLAPercentage;
    }

    public int getMaxExpectedRes() {
        return maxExpectedRes;
    }

    public void setMaxExpectedRes(int maxExpectedRes) {
        this.maxExpectedRes = maxExpectedRes;
    }

    public boolean isSLAviolation() {
        return SLAviolation;
    }

    public void setSLAviolation(boolean sLAviolation) {
        SLAviolation = sLAviolation;
    }

    protected String getBladeType() {
        return bladeType;
    }

    protected void setBladeType(String bladeType) {
        this.bladeType = bladeType;
    }

    /*
     * double getMeanResTimeLastEpoch() {
     * 
     * if (resTimeEpoch == 0) // the first time in { resTimeEpoch = respTime;
     * totalJobEpoch = totalJob - queueLength; LOGGER.info(
     * "First   Last Epoch   " + respTime + totalJobEpoch + "\t" + chassisID);
     * if (totalJobEpoch > 0) return respTime / totalJobEpoch; else return 0; }
     * else { double tempTime = respTime - resTimeEpoch; double tempJob =
     * totalJob - queueLength - totalJobEpoch; resTimeEpoch = respTime;
     * totalJobEpoch = totalJob - queueLength; LOGGER.info(
     * "in get MeanResponse Last Epoch   " + tempTime / tempJob + "\t" +
     * chassisID); if (tempJob != 0) return tempTime / tempJob; else return 0; }
     * }
     */
}

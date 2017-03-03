package se.kth.id2203.distributor;

import com.larskroll.common.J6;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.id2203.failuredetection.DistributorRequestEvent;
import se.kth.id2203.failuredetection.DistributorResponseEvent;
import se.kth.id2203.networking.Message;
import se.kth.id2203.networking.NetAddress;
import se.kth.id2203.overlay.*;
import se.sics.kompics.*;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;

import java.util.ArrayList;
import java.util.UUID;


/**
 * Created by Amir on 2017-02-22.
 */
public class DistributorComponent extends ComponentDefinition {
    protected final Negative<Routing> route = provides(Routing.class);
    final static Logger LOG = LoggerFactory.getLogger(DistributorComponent.class);
    final NetAddress self = config().getValue("id2203.project.address", NetAddress.class);
    ArrayList<NetAddress> leaders = new ArrayList<>();
    protected final Positive<DistributionPort> distribution = requires(DistributionPort.class);
    protected final Positive<Network> net = requires(Network.class);
    protected final Positive<Timer> timer = requires(Timer.class);
    private UUID timeoutId;
    protected LookupTable lut = new LookupTable();
    protected int groupDivision = 0;
    protected int keyInterval = Integer.MAX_VALUE;

    protected  ArrayList<Integer> grpValHolder = null;

    protected final Handler<SendLookupTable> lookUpTableHandler = new Handler<SendLookupTable>() {

        @Override
        public void handle(SendLookupTable lookupTable) {
            LookupTable table = (LookupTable) lookupTable.lookupTable;
            lut = table;
            LOG.debug("SendLookupTable är " + table);
            LOG.debug("na.getNodes är:" + table.getNodes());

            groupDivision = table.getNodes().size();
            grpValHolder = new ArrayList<Integer>(groupDivision);

            setKeyIntervals();


            LOG.debug("groupDivision är " + groupDivision + " och keyInterval är " + keyInterval);
            for(ArrayList<NetAddress> group : table.getNodes()){

                for(int i = 1; i<group.size(); i++){
                    trigger(new Message(self, group.get(i), new LeaderNotification("You are not the leader")), net);
                }
                NetAddress leader = group.get(0);
                leaders.add(leader);
                 trigger(new Message(self, leader, new LeaderNotification("You are the leader")), net);
            }
        }
    };

    private void setKeyIntervals() {

        for(int i = 0; i<groupDivision-1; i++){
            grpValHolder.add((i+1) * (keyInterval/groupDivision));
        }

        for(int i = 0; i<groupDivision-1; i++){
            LOG.debug("grpValHolder #" + i + " innehåller " + grpValHolder.get(i));
        }

    }

    protected final ClassMatchedHandler<RouteMsg, Message> routeHandler = new ClassMatchedHandler<RouteMsg, Message>() {

        @Override
        public void handle(RouteMsg content, Message context) {
           // ArrayList<NetAddress> partition = lut.lookup(Integer.parseInt(content.key));
           // NetAddress target = J6.randomElement(partition);

            for(int i = 0; i<grpValHolder.size(); i++){
                if (Integer.parseInt(content.key) <= grpValHolder.get(i)){
                    LOG.info("Forwarding message with operation {} for key {} with value {} to {}",content.operation, content.key, content.value, leaders.get(i));
                    trigger(new Message(context.getSource(), leaders.get(i), content.msg), net);
                    break;
                }
            }

        }
    };
    protected final Handler<RouteMsg> localRouteHandler = new Handler<RouteMsg>() {

        @Override
        public void handle(RouteMsg event) {
            ArrayList<NetAddress> partition = lut.lookup(Integer.parseInt(event.key));
            NetAddress target = J6.randomElement(partition);
            LOG.info("Routing message for key {} to {}", event.key, target);
            trigger(new Message(self, target, event.msg), net);
        }
    };

    protected final ClassMatchedHandler<DistributorRequestEvent, Message> DHBHandler = new ClassMatchedHandler<DistributorRequestEvent, Message>() {
        @Override
        public void handle(DistributorRequestEvent distributorRequestEvent, Message message) {

            LOG.debug("I am distributor: " + self + " and I received " + distributorRequestEvent.heartbeat + " from " + message.getSource());
            trigger(new Message(self, message.getSource(), new DistributorResponseEvent("Yes I am alive as a distributor")), net);

        }
    };

    protected final ClassMatchedHandler<Connect, Message> connectHandler = new ClassMatchedHandler<Connect, Message>() {

        @Override
        public void handle(Connect content, Message context) {
            LOG.debug("VI ÄR I CONNECTHANDLER MED LUT: " + lut.toString());
            if (lut != null) {
                LOG.debug("Accepting connection request from {}", context.getSource());
                int size = lut.getNodes().size();
                trigger(new Message(self, context.getSource(), content.ack(size)), net);
            } else {
                LOG.info("Rejecting connection request from {}, as system is not ready, yet.", context.getSource());
            }
        }
    };


    {
        subscribe(connectHandler, net);
        subscribe(DHBHandler, net);
        subscribe(lookUpTableHandler, distribution);
        subscribe(routeHandler, net);
        //subscribe(localRouteHandler, route);
}
}

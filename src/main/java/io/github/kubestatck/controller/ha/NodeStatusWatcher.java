/**
 * Copyright (2022, ) Institute of Software, Chinese Academy of Sciences
 */
package io.github.kubestatck.controller.ha;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.Status;
import io.github.kubestack.client.KubeStackClient;
import io.github.kubestack.client.api.models.Node;
import io.github.kubestack.client.api.models.VirtualMachine;
import io.github.kubesys.client.KubernetesClient;
import io.github.kubesys.client.KubernetesWatcher;

/**
 * @author shizhonghao17@otcaix.iscas.ac.cn
 * @author yangchen18@otcaix.iscas.ac.cn
 * @author wuheng@otcaix.iscas.ac.cn
 * @since Wed May 01 17:26:22 CST 2019
 * 
 *        https://www.json2yaml.com/ http://www.bejson.com/xml2json/
 * 
 *        debug at runWatch method of
 *        io.fabric8.kubernetes.client.dsl.internal.WatchConnectionManager
 **/
public class NodeStatusWatcher extends KubernetesWatcher {

	protected final static Logger m_logger = Logger.getLogger(NodeStatusWatcher.class.getName());

	public NodeStatusWatcher(KubernetesClient client) {
		super(client);
	}

	protected boolean isShutDown(Map<String, Object> status) {
		return status.get("reason").equals("ShutDown");
	}


	@Override
	public void doAdded(JsonNode node) {
		// ignore here
	}

	@Override
	public void doModified(JsonNode json) {
		Node node = new ObjectMapper().convertValue(json, Node.class);
		String nodeName = node.getMetadata().getName();
		if (nodeName.startsWith("vm.") && NodeSelectorImpl.notReady(node)) {
			
			Map<String, String> labels = new HashMap<String, String>();
			labels.put("host", nodeName);

			try {
				for (VirtualMachine vm : ((KubeStackClient) client).virtualMachines().list(labels)) {
					Status status = vm.getSpec().getStatus();
					Map<String, Object> statusProps = status.getAdditionalProperties();
					Map<String, Object> statusCond = (Map<String, Object>) (statusProps.get("conditions"));
					Map<String, Object> statusStat = (Map<String, Object>) (statusCond.get("state"));
					Map<String, Object> statusWait = (Map<String, Object>) (statusStat.get("waiting"));
					statusWait.put("reason", "Shutdown");

					client.updateResourceStatus(new ObjectMapper().convertValue(vm, JsonNode.class));
					
					Event item = new Event();
					ObjectReference involvedObject = new ObjectReference();
					involvedObject.setKind(VirtualMachine.class.getSimpleName());
					involvedObject.setName(vm.getMetadata().getName());
					involvedObject.setNamespace(vm.getMetadata().getNamespace());
					item.setInvolvedObject(involvedObject );
					item.setReason("ShutdownVM");
					client.createResource(new ObjectMapper().convertValue(item, JsonNode.class));
				}
			} catch (Exception e) {
				m_logger.severe("Error to modify the VM's status:" + e.getCause());
			}
		}
	}

	@Override
	public void doDeleted(JsonNode node) {
		// ignore here
	}

	@Override
	public void doClose() {
		m_logger.log(Level.INFO, "Stop NodeStatusWatcher");
	}
}

package io.tlf.dockertest;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.DockerCmdExecFactory;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.netty.NettyDockerCmdExecFactory;
import java.io.IOException;

/**
 *
 * @author Trevor Flynn
 */
public class Main {

    private static final String REGISTRY_URL = "localhost:5000";
    private static final String DOCKER_SERVER = "localhost";
    private static final int DOCKER_PORT = 2375;
    //
    private static final DockerClientConfig DOCKER_CONFIG = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost("tcp://" + DOCKER_SERVER + ":" + DOCKER_PORT)
            .withRegistryUrl(REGISTRY_URL)
            .build();

    private static final DockerCmdExecFactory DOCKER_CMD_FACTORY = new NettyDockerCmdExecFactory();

    private static DockerClient DOCKER_CLIENT = null;

    private static DockerClient client() {
        if (DOCKER_CLIENT == null) {
            DOCKER_CLIENT = DockerClientBuilder.getInstance(DOCKER_CONFIG)
                    .withDockerCmdExecFactory(DOCKER_CMD_FACTORY)
                    .build();
        }
        //the client will use the pool for connections
        return DOCKER_CLIENT;
    }

    public static void main(String[] args) throws InterruptedException {
        //Lets get info first
        Info info = client().infoCmd().exec();
        System.out.println(info);
        //Lets build a container
        System.out.println("Creating container");
        CreateContainerResponse containerResponse = client().createContainerCmd("fedora")
                .withCmd("sleep", "infinity")
                .exec();

        System.out.println("Running container");
        client().startContainerCmd(containerResponse.getId()).exec();

        System.out.println("Setting up bash");
        ExecCreateCmdResponse execCreateCmdResponse = client().execCreateCmd(containerResponse.getId())
                .withAttachStdout(true)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withTty(true)
                .withCmd("bash")
                .exec();

        System.out.println("Running bash");
        ExecStartResultCallback tunnels;
        client().execStartCmd(execCreateCmdResponse.getId())
                .withDetach(false)
                .withStdIn(System.in)
                .exec(tunnels = new ExecStartResultCallback(System.out, System.err))
                .awaitCompletion();
        System.out.println("Shutting down container");
        client().stopContainerCmd(containerResponse.getId()).exec();
        //Lets get info for the container
        info = client().infoCmd().exec();
        System.out.println(info);
        try {
            client().close();
            DOCKER_CMD_FACTORY.close();
           tunnels.close();
        } catch (IOException ex) {
            //Connection already closed
            ex.printStackTrace();
        }
        System.out.println("Connection to docker server closed");
    }
}

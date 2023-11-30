"use client";
import React from "react";

import {
  Box,
  Flex,
  Text,
  Progress,
  Input,
  Button,
  Image,
  Select,
} from "@chakra-ui/react";
import { fetchNetworkParticipants } from "@/api/gateway";

const useForceUpdate = () => React.useState()[1];

type ParticipantsPopUpProps = {
  activeHoldingId: string;
  setActiveHoldingId: Function;
  openNetworkParticipants: boolean;
  setOpenNetworkParticipants: Function;
};

const ParticipantsPopUp = ({
  activeHoldingId,
  setActiveHoldingId,
  openNetworkParticipants,
  setOpenNetworkParticipants,
}: ParticipantsPopUpProps) => {
  const [networkParticipants, setNetworkParticipants] = React.useState([]);

  const forceUpdate = useForceUpdate();
  const getInfo = async () => {
    // alert(`getInfo`);
    const data = await fetchNetworkParticipants();
    console.log("Netowkr Participants: ", data);
    setNetworkParticipants(data);
  };

  React.useEffect(() => {
    getInfo();
  }, []);

  console.log("activeHoldingId: ", activeHoldingId);

  return (
    <Box
      bg="white"
      borderRadius="10"
      p="10"
      m="10"
      marginBottom="30"
      textAlign="center"
      fontFamily={"Roboto"}
      position="fixed"
      top="10"
      left="10"
      zIndex={2}
      display={openNetworkParticipants ? "block" : "none"}
    >
      <Button
        onClick={() => {
          setOpenNetworkParticipants(false);
        }}
      >
        Close
      </Button>
      <Text fontFamily={"Roboto"} fontSize={"20"} fontWeight={"bold"}>
        Network Participants
      </Text>

      {/* {networkParticipants.map((participant, i) => (
        <Box
          bg="white"
          borderRadius="10"
          p="10"
          m="10"
          textAlign="center"
          fontFamily={"Roboto"}
          key={i}
        >
          <Text fontFamily={"Roboto"}>
            {participant.holdingIdentity.x500Name}
          </Text>
          <Select 
        </Box>
      ))} */}
      <select
        onChange={(e) => {
          setActiveHoldingId(e.target.value);
          forceUpdate();
        }}
        value={activeHoldingId}
      >
        {networkParticipants.map((participant, i) => (
          <option value={participant.holdingIdentity.shortHash}>
            {participant.holdingIdentity.x500Name}
          </option>
        ))}
      </select>
    </Box>
  );
};

export default ParticipantsPopUp;

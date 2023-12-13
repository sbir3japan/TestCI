"use client";

import React, { useEffect } from "react";
import {
  Box,
  Flex,
  Text,
  Progress,
  Input,
  Button,
  Image,
} from "@chakra-ui/react";
import DialogElement from "@/components/Dialog";
import { fetchTokens, postFlowData } from "@/api/gateway";
import toast, { Toaster } from "react-hot-toast";
import { Web3 } from "web3";
import { abi } from "@/assets/abi";
import Sidebar from "@/components/Sidebar";
import { ActiveHoldingIdContext } from "@/context/activeHoldingIdContext";

type AssetProps = {
  queryParams: {
    name: string;
    price: number;
    image: string;
  };
  router: any;
  setOpenNetworkParticipants: Function;
};

const Asset = ({
  queryParams,
  router,
  setOpenNetworkParticipants,
}: AssetProps) => {
  const [purchasePercentage, setPurchasePercentage] = React.useState(0);
  const [purchasePrice, setPurchasePrice] = React.useState(0);
  const [purchaseAmount, setPurchaseAmount] = React.useState(0);
  const [open, setOpen] = React.useState(false);
  const [funded, setFunded] = React.useState(0);
  const { activeHoldingId } = React.useContext(ActiveHoldingIdContext);

  const fractions = 1000000;

  const fetchFundedInfo = async () => {
    try {
      const web3 = new Web3("http://localhost:8545");
      const contractAddress = process.env.FRACTIONAL_CONTRACT_ADDRESS;
      const mainAddress = "0xf675aEfFf9019E580c61Dbaf1f22BD43249143A3";
      const contractInst = new web3.eth.Contract(abi);

      const balance = contractInst.methods
        .balanceOf(mainAddress, 1)
        .encodeABI();
      const decodedBalance = await web3.eth.call({
        to: contractAddress,
        data: balance,
      });
      const ownerBalance = parseInt(decodedBalance.toString());
      console.log(ownerBalance);
      setFunded((ownerBalance / fractions) * 100);

      // const balance = await contract.methods.balanceOf(mainAddress, 1).call();
      // const ownerBalance = parseInt(balance.toString())
    } catch (e) {
      console.log("🚀 ~ file: asset.tsx ~ line 41 ~ fetchFundedInfo ~ e", e);
    }
  };

  useEffect(() => {
    fetchFundedInfo();
  }, [queryParams.price]);

  const handlePurchase = async () => {
    try {
      console.log("🏦 purchasePrice: ", purchasePrice);
      console.log("💸 purchasePercentage: ", purchasePercentage);
      const data = await fetchTokens(activeHoldingId);

      const out = await postFlowData(
        purchasePrice,
        data.id,
        purchaseAmount,
        activeHoldingId
      );
      console.log("🎉 Sussesfull Purchase: ", out);
      const path = `/success?hash=${out}&name=${queryParams.name}&price=${queryParams.price}&image=${queryParams.image}&amount=${purchasePrice}&percentage=${purchasePercentage}`;
      // navigate using window
      router.push(path);
    } catch (e) {
      console.log("🚀 ~ file: asset.tsx ~ line 69 ~ handlePurchase ~ e", e);
    }
  };
  return (
    <Flex
      flexDirection={"row"}
      justifyContent={"space-between"}
      alignItems={"center"}
    >
      {/* Navbar */}

      <Flex justifyContent={"space-between"} marginTop={150}>
        <Toaster position="top-right" reverseOrder={false} />
        <DialogElement
          open={open}
          setOpen={setOpen}
          onComplete={() => {
            toast.promise(handlePurchase(), {
              loading: "Buying...",
              success: <b>Bought!</b>,
              error: <b>Something went wrong!</b>,
            });
          }}
        />

        <Sidebar setOpenNetworkParticipants={setOpenNetworkParticipants} />
        <Box w="50%" h="100vh" p="10" m="10">
          <Box>
            <Box>
              <Image
                borderRadius={10}
                w="100%"
                src={queryParams.image}
                alt="main-image"
              />
            </Box>
          </Box>
        </Box>
        <Box bg="#EC1D24" p="30" m="10" borderRadius={"30"}>
          <Box marginBottom={50} bg="black" p="10" borderRadius="10">
            <Text
              fontSize={24}
              marginBottom={10}
              fontFamily={"Roboto"}
              color="white"
            >
              {queryParams.name}
            </Text>
            <Text
              fontSize={18}
              marginBottom={10}
              fontFamily={"Roboto"}
              color="white"
            >
              {queryParams.price} AED{" "}
            </Text>
            <Text marginBottom={10} fontFamily={"Roboto"} color="white">
              {funded}% Amount Funded
            </Text>

            <Progress value={50} backgroundColor="red" />
          </Box>
          <Box bg="white" p="30" borderRadius="30" marginBottom={50}>
            <Text fontFamily={"Roboto"}>Project outcome (annual)</Text>
            <Text fontFamily={"Roboto"}>
              Annual Earnings (total): {queryParams.price * 0.6} AED
            </Text>
            <Text fontFamily={"Roboto"}>Gross Profit</Text>
          </Box>
          <Box bg="white" p="30" borderRadius="30" marginBottom={50}>
            <Flex flexDirection={"column"}>
              <Input
                placeholder="Buy %"
                marginBottom={10}
                padding={10}
                borderRadius={10}
                onChange={(e) => {
                  const amount = parseInt(e.target.value);
                  setPurchasePercentage(amount);
                  setPurchasePrice(queryParams.price * (amount / 100));
                  setPurchaseAmount(fractions * (amount / 100));
                }}
              />
              <Input
                readOnly={true}
                placeholder="Cost in total"
                padding={10}
                borderRadius={10}
                value={`${purchasePrice} AED`}
              />
            </Flex>
          </Box>
          <Flex justifyContent={"center"}>
            <Button
              backgroundColor="darkgreen"
              color="white"
              padding={15}
              borderRadius={10}
              onClick={() => {
                setOpen(true);
              }}
              fontFamily={"Roboto"}
            >
              Place Offer
            </Button>
          </Flex>
        </Box>
      </Flex>
    </Flex>
  );
};

export default Asset;

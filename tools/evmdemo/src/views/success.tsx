import React, { useEffect } from "react";
import { Box, Heading, Text, Image, Flex } from "@chakra-ui/react";
import { useRouter } from "next/router";
import { Web3 } from "web3";
import { abi } from "@/assets/abi";
// import DonutChart from "react-donut-chart";
import Sidebar from "@/components/Sidebar";
import dynamic from "next/dynamic";
const DynamicReactJson = dynamic(import("react-json-view"), { ssr: false });
const DonutChart = dynamic(() => import("react-donut-chart"), { ssr: false });

type SuccessProps = {
  setOpenNetworkParticipants: Function;
};

export default function Success({ setOpenNetworkParticipants }: SuccessProps) {
  const router = useRouter();
  const { name, price, funded, image, hash } = router.query;
  const [tr, setTr] = React.useState("");
  const [mainBalance, setMainBalance] = React.useState(0);
  const [buyerBalance, setBuyerBalance] = React.useState(0);
  const [clientLoaded, setClientLoaded] = React.useState(false);
  useEffect(() => {
    // check if document exists
    if (typeof window !== "undefined") {
      setClientLoaded(true);
    }
  }, []);

  const fetchTransactionInfo = async () => {
    const web3 = new Web3("http://localhost:8545");
    console.log("₿ Transaction Hash: ", hash);
    let transaction = await web3.eth.getTransactionReceipt(hash);
    // remove logsbloom
    delete transaction.logsBloom;

    const functionalString = JSON.stringify(
      transaction,
      (key, value) => (typeof value === "bigint" ? value.toString() : value) // return everything else unchanged
    );
    setTr(JSON.parse(functionalString));

    //     const prettyString = JSON.stringify(JSON.parse(functionalString), null, "\t");
    //     setTr(prettyString)
    const contractAddress = process.env.FRACTIONAL_CONTRACT_ADDRESS;
    const contractInst = new web3.eth.Contract(abi, contractAddress);

    // query the balance of the main address
    const mainAddress = "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73";
    const buyerAddress = "0xf675aEfFf9019E580c61Dbaf1f22BD43249143A3";

    const balanceMethod = await contractInst.methods
      .balanceOf(mainAddress, 1)
      .encodeABI();
    const buyerBalanceMethod = await contractInst.methods
      .balanceOf(buyerAddress, 1)
      .encodeABI();

    const mainBalance = await web3.eth.call({
      to: contractAddress,
      data: balanceMethod,
    });
    const buyerBalance = await web3.eth.call({
      to: contractAddress,
      data: buyerBalanceMethod,
    });

    console.log("📱 MAIN BALANCE: ", mainBalance);
    console.log("📱 BUYER BALANCE: ", buyerBalance);

    setMainBalance(parseInt(mainBalance.toString()));
    setBuyerBalance(parseInt(buyerBalance.toString()));

    // const balance = await contract.methods.balanceOf(mainAddress, 1).call();
    // const buyerBalance = await contract.methods
    //   .balanceOf(buyerAddress, 1)
    //   .call();

    // setMainBalance(parseInt(balance.toString()));
    // setBuyerBalance(parseInt(buyerBalance.toString()));
    // process.env.FRACTIONAL_CONTRACT_ADDRESS
  };
  useEffect(() => {
    fetchTransactionInfo();
  }, []);

  return (
    <Flex textAlign="center" marginTop={150}>
      <Sidebar setOpenNetworkParticipants={setOpenNetworkParticipants} />
      <Flex
        flexDirection={"column"}
        backgroundColor="white"
        paddingTop={50}
        borderRadius={10}
      >
        <Flex
          minWidth="calc(100vw - 350px)"
          justifyContent={"space-evenly"}
          marginBottom={50}
          borderRadius={30}
        >
          <Box padding={30} borderRadius={10}>
            <Heading as="h1" size="2xl" mb={4} fontFamily={"Roboto"}>
              Thank you for your purchase!
            </Heading>
            <Text fontSize="xl" fontWeight="bold" mb={4} fontFamily={"Roboto"}>
              {name}
            </Text>
            <Text fontSize="lg" mb={4} fontFamily={"Roboto"}>
              Total Price: {price}
            </Text>
            <Text fontSize="lg" mb={4} fontFamily={"Roboto"}>
              Your Share:{" "}
              {((buyerBalance / (mainBalance + buyerBalance)) * 100).toFixed(2)}
              % of the total
            </Text>
          </Box>

          <Image src={image} alt={name} maxW="500" borderRadius="10" />
        </Flex>

        <Flex>
          <Box>
            <Box style={{ maxWidth: 600, position: "relative" }}>
              <Text
                fontFamily={"Roboto"}
                width="100%"
                textAlign={"left"}
                marginLeft="5%"
                fontSize={"20"}
              >
                Ownership (%)
              </Text>
              <DonutChart
                data={[
                  {
                    label: "Your Share",
                    value: (buyerBalance / (mainBalance + buyerBalance)) * 100,
                  },
                  {
                    label: "",
                    value: (mainBalance / (mainBalance + buyerBalance)) * 100,
                    isEmpty: true,
                  },
                ]}
                width={500}
                colors={["red", "black"]}
              />
            </Box>
            <Box></Box>
          </Box>

          <Box paddingLeft="5%">
            <Text
              fontFamily={"Roboto"}
              width="100%"
              textAlign={"left"}
              marginLeft="5%"
              fontSize={"20"}
            >
              EVM Transaction
            </Text>

            <DynamicReactJson
              src={tr}
              style={{ textAlign: "left", maxWidth: 700, overflow: "scroll" }}
            />
          </Box>
        </Flex>
      </Flex>
    </Flex>
  );
}

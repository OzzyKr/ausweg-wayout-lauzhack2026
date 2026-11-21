package ch.ausweg.shared.ojp

/**
 * Hand-written OJP 2.0 responses, following the opentransportdata.swiss cookbook and the VDV OJP 2.0 XSD.
 * Stop refs, platforms and times are illustrative (Lausanne → Fribourg, 25 Sep 2026); nothing personal.
 * Replace / complement with scrubbed real recordings once we have an API key.
 *
 * Not yet confirmed against a real response: the `Situations` wrapper in TripResponseContext and the
 * trip error shape. The parser deliberately searches for those by element name, not by exact path.
 */
object OjpFixtures {
    const val TRIPS =
        """<?xml version="1.0" encoding="UTF-8"?>
<OJP xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri" version="2.0">
  <OJPResponse>
    <siri:ServiceDelivery>
      <siri:ResponseTimestamp>2026-09-25T15:10:00Z</siri:ResponseTimestamp>
      <siri:ProducerRef>EFAController</siri:ProducerRef>
      <OJPTripDelivery>
        <siri:ResponseTimestamp>2026-09-25T15:10:00Z</siri:ResponseTimestamp>
        <siri:Status>true</siri:Status>
        <CalcTime>412</CalcTime>
        <TripResponseContext>
          <Places>
            <Place>
              <StopPlace>
                <StopPlaceRef>ch:1:sloid:1120</StopPlaceRef>
                <StopPlaceName><Text xml:lang="de">Lausanne</Text></StopPlaceName>
              </StopPlace>
              <Name><Text xml:lang="de">Lausanne</Text></Name>
              <GeoPosition><siri:Longitude>6.62909</siri:Longitude><siri:Latitude>46.51679</siri:Latitude></GeoPosition>
            </Place>
          </Places>
          <Situations>
            <PtSituation>
              <siri:CreationTime>2026-09-25T14:31:08Z</siri:CreationTime>
              <siri:ParticipantRef>ch:1:sboid:100001</siri:ParticipantRef>
              <siri:SituationNumber>sit-puidoux-fire</siri:SituationNumber>
              <siri:Version>2</siri:Version>
              <siri:ValidityPeriod>
                <siri:StartTime>2026-09-25T14:31:00Z</siri:StartTime>
                <siri:EndTime>2026-09-25T16:45:00Z</siri:EndTime>
              </siri:ValidityPeriod>
              <siri:AlertCause>fire</siri:AlertCause>
              <siri:PublishingActions>
                <siri:PublishingAction>
                  <siri:PassengerInformationAction>
                    <siri:TextualContent>
                      <siri:SummaryContent>
                        <siri:SummaryText xml:lang="en">Interrupted service: Puidoux - Vauderens</siri:SummaryText>
                      </siri:SummaryContent>
                      <siri:DescriptionContent>
                        <siri:DescriptionText xml:lang="en">No trains between Puidoux and Vauderens &amp; Lucens because of a fire.</siri:DescriptionText>
                      </siri:DescriptionContent>
                    </siri:TextualContent>
                  </siri:PassengerInformationAction>
                </siri:PublishingAction>
              </siri:PublishingActions>
            </PtSituation>
            <PtSituation>
              <siri:CreationTime>2026-09-25T13:00:00Z</siri:CreationTime>
              <siri:ParticipantRef>ch:1:sboid:100001</siri:ParticipantRef>
              <siri:SituationNumber>sit-lift-fribourg</siri:SituationNumber>
              <siri:ValidityPeriod><siri:StartTime>2026-09-25T13:00:00Z</siri:StartTime></siri:ValidityPeriod>
              <siri:Language>fr</siri:Language>
              <siri:Summary>Ascenseur hors service</siri:Summary>
              <siri:Description>Ascenseur du quai 2 hors service à Fribourg/Freiburg.</siri:Description>
            </PtSituation>
          </Situations>
        </TripResponseContext>
        <TripResult>
          <Id>ID-direct</Id>
          <Trip>
            <Id>T-direct</Id>
            <Duration>PT46M</Duration>
            <StartTime>2026-09-25T15:20:00Z</StartTime>
            <EndTime>2026-09-25T16:06:00Z</EndTime>
            <Transfers>0</Transfers>
            <Leg>
              <Id>1</Id>
              <Duration>PT46M</Duration>
              <TimedLeg>
                <LegBoard>
                  <siri:StopPointRef>ch:1:sloid:1120:7:7</siri:StopPointRef>
                  <StopPointName><Text xml:lang="de">Lausanne</Text></StopPointName>
                  <PlannedQuay><Text xml:lang="de">7</Text></PlannedQuay>
                  <EstimatedQuay><Text xml:lang="de">8</Text></EstimatedQuay>
                  <ServiceDeparture>
                    <TimetabledTime>2026-09-25T15:20:00Z</TimetabledTime>
                    <EstimatedTime>2026-09-25T15:23:00Z</EstimatedTime>
                  </ServiceDeparture>
                  <Order>1</Order>
                </LegBoard>
                <LegIntermediate>
                  <siri:StopPointRef>ch:1:sloid:4012:1:1</siri:StopPointRef>
                  <StopPointName><Text xml:lang="de">Puidoux-Chexbres</Text></StopPointName>
                  <ServiceArrival><TimetabledTime>2026-09-25T15:31:00Z</TimetabledTime></ServiceArrival>
                  <ServiceDeparture><TimetabledTime>2026-09-25T15:31:00Z</TimetabledTime></ServiceDeparture>
                  <Order>2</Order>
                  <NotServicedStop>true</NotServicedStop>
                </LegIntermediate>
                <LegIntermediate>
                  <siri:StopPointRef>ch:1:sloid:4021:1:1</siri:StopPointRef>
                  <StopPointName><Text xml:lang="de">Romont FR</Text></StopPointName>
                  <ServiceArrival><TimetabledTime>2026-09-25T15:45:00Z</TimetabledTime></ServiceArrival>
                  <ServiceDeparture><TimetabledTime>2026-09-25T15:46:00Z</TimetabledTime></ServiceDeparture>
                  <Order>3</Order>
                </LegIntermediate>
                <LegAlight>
                  <siri:StopPointRef>ch:1:sloid:4100:2:2</siri:StopPointRef>
                  <StopPointName><Text xml:lang="de">Fribourg/Freiburg</Text></StopPointName>
                  <PlannedQuay><Text xml:lang="de">2</Text></PlannedQuay>
                  <ServiceArrival>
                    <TimetabledTime>2026-09-25T16:06:00Z</TimetabledTime>
                    <EstimatedTime>2026-09-25T16:09:00Z</EstimatedTime>
                  </ServiceArrival>
                  <Order>4</Order>
                </LegAlight>
                <Service>
                  <OperatingDayRef>2026-09-25</OperatingDayRef>
                  <JourneyRef>ch:1:sjyid:100001:2515-001</JourneyRef>
                  <PublicCode>IR15</PublicCode>
                  <siri:LineRef>ojp:91015:A</siri:LineRef>
                  <siri:DirectionRef>H</siri:DirectionRef>
                  <Mode>
                    <PtMode>rail</PtMode>
                    <siri:RailSubmode>interregionalRail</siri:RailSubmode>
                    <Name><Text xml:lang="de">Zug</Text></Name>
                  </Mode>
                  <PublishedServiceName><Text xml:lang="de">IR15</Text></PublishedServiceName>
                  <TrainNumber>2515</TrainNumber>
                  <DestinationText><Text xml:lang="de">Luzern</Text></DestinationText>
                  <SituationFullRefs>
                    <SituationFullRef>
                      <siri:ParticipantRef>ch:1:sboid:100001</siri:ParticipantRef>
                      <siri:SituationNumber>sit-puidoux-fire</siri:SituationNumber>
                    </SituationFullRef>
                  </SituationFullRefs>
                </Service>
              </TimedLeg>
            </Leg>
          </Trip>
        </TripResult>
        <TripResult>
          <Id>ID-bus</Id>
          <Trip>
            <Id>T-bus</Id>
            <Duration>PT1H20M</Duration>
            <StartTime>2026-09-25T15:25:00Z</StartTime>
            <EndTime>2026-09-25T16:45:00Z</EndTime>
            <Leg>
              <Id>1</Id>
              <TimedLeg>
                <LegBoard>
                  <siri:StopPointRef>ch:1:sloid:1120:5:5</siri:StopPointRef>
                  <StopPointName><Text xml:lang="de">Lausanne</Text></StopPointName>
                  <PlannedQuay><Text xml:lang="de">5</Text></PlannedQuay>
                  <ServiceDeparture><TimetabledTime>2026-09-25T15:25:00Z</TimetabledTime></ServiceDeparture>
                  <Order>1</Order>
                </LegBoard>
                <LegAlight>
                  <siri:StopPointRef>ch:1:sloid:4012:2:2</siri:StopPointRef>
                  <StopPointName><Text xml:lang="de">Puidoux-Chexbres</Text></StopPointName>
                  <PlannedQuay><Text xml:lang="de">2</Text></PlannedQuay>
                  <ServiceArrival><TimetabledTime>2026-09-25T15:38:00Z</TimetabledTime></ServiceArrival>
                  <Order>4</Order>
                </LegAlight>
                <Service>
                  <OperatingDayRef>2026-09-25</OperatingDayRef>
                  <JourneyRef>ch:1:sjyid:100001:30-001</JourneyRef>
                  <Mode><PtMode>rail</PtMode></Mode>
                  <PublishedServiceName><Text xml:lang="de">S3</Text></PublishedServiceName>
                  <DestinationText><Text xml:lang="de">Puidoux-Chexbres</Text></DestinationText>
                </Service>
              </TimedLeg>
            </Leg>
            <Leg>
              <Id>2</Id>
              <Duration>PT4M</Duration>
              <TransferLeg>
                <TransferType>walk</TransferType>
                <LegStart>
                  <siri:StopPointRef>ch:1:sloid:4012:2:2</siri:StopPointRef>
                  <Name><Text xml:lang="de">Puidoux-Chexbres</Text></Name>
                </LegStart>
                <LegEnd>
                  <StopPlaceRef>ch:1:sloid:4012</StopPlaceRef>
                  <Name><Text xml:lang="de">Puidoux-Chexbres, gare (bus de remplacement)</Text></Name>
                </LegEnd>
                <TimeWindowStart>2026-09-25T15:38:00Z</TimeWindowStart>
                <TimeWindowEnd>2026-09-25T15:42:00Z</TimeWindowEnd>
                <Duration>PT4M</Duration>
              </TransferLeg>
            </Leg>
            <Leg>
              <Id>3</Id>
              <TimedLeg>
                <LegBoard>
                  <siri:StopPointRef>ch:1:sloid:4012:10:10</siri:StopPointRef>
                  <StopPointName><Text xml:lang="de">Puidoux-Chexbres, gare</Text></StopPointName>
                  <ServiceDeparture><TimetabledTime>2026-09-25T15:45:00Z</TimetabledTime></ServiceDeparture>
                  <Order>1</Order>
                </LegBoard>
                <LegAlight>
                  <siri:StopPointRef>ch:1:sloid:4100:20:20</siri:StopPointRef>
                  <StopPointName><Text xml:lang="de">Fribourg/Freiburg, gare</Text></StopPointName>
                  <ServiceArrival><TimetabledTime>2026-09-25T16:45:00Z</TimetabledTime></ServiceArrival>
                  <Order>2</Order>
                </LegAlight>
                <Service>
                  <OperatingDayRef>2026-09-25</OperatingDayRef>
                  <JourneyRef>ch:1:sjyid:100001:EV-001</JourneyRef>
                  <Mode><PtMode>bus</PtMode><siri:BusSubmode>railReplacementBus</siri:BusSubmode></Mode>
                  <PublishedServiceName><Text xml:lang="de">EV</Text></PublishedServiceName>
                  <DestinationText><Text xml:lang="de">Fribourg/Freiburg</Text></DestinationText>
                </Service>
              </TimedLeg>
            </Leg>
          </Trip>
        </TripResult>
        <TripResult>
          <Id>ID-cancelled</Id>
          <Trip>
            <Id>T-cancelled</Id>
            <StartTime>2026-09-25T15:45:00Z</StartTime>
            <EndTime>2026-09-25T16:27:00Z</EndTime>
            <Transfers>0</Transfers>
            <Leg>
              <Id>1</Id>
              <TimedLeg>
                <LegBoard>
                  <siri:StopPointRef>8501120</siri:StopPointRef>
                  <StopPointName><Text xml:lang="de">Lausanne</Text></StopPointName>
                  <ServiceDeparture><TimetabledTime>2026-09-25T15:45:00Z</TimetabledTime></ServiceDeparture>
                </LegBoard>
                <LegAlight>
                  <siri:StopPointRef>8504100</siri:StopPointRef>
                  <StopPointName><Text xml:lang="de">Fribourg/Freiburg</Text></StopPointName>
                  <ServiceArrival><TimetabledTime>2026-09-25T16:27:00Z</TimetabledTime></ServiceArrival>
                </LegAlight>
                <Service>
                  <Mode><PtMode>rail</PtMode></Mode>
                  <PublishedServiceName><Text xml:lang="de">IC1</Text></PublishedServiceName>
                  <Cancelled>true</Cancelled>
                </Service>
              </TimedLeg>
            </Leg>
          </Trip>
        </TripResult>
      </OJPTripDelivery>
    </siri:ServiceDelivery>
  </OJPResponse>
</OJP>"""

    /** Shape not yet confirmed against a real response (see class doc). */
    const val TRIPS_NOT_FOUND =
        """<?xml version="1.0" encoding="UTF-8"?>
<OJP xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri" version="2.0">
  <OJPResponse>
    <siri:ServiceDelivery>
      <siri:ResponseTimestamp>2026-09-25T15:10:00Z</siri:ResponseTimestamp>
      <OJPTripDelivery>
        <siri:ResponseTimestamp>2026-09-25T15:10:00Z</siri:ResponseTimestamp>
        <siri:Status>false</siri:Status>
        <siri:ErrorCondition>
          <OJPError>
            <siri:ErrorText>No trip found</siri:ErrorText>
            <TripProblemType>TRIP_NOTRIPFOUND</TripProblemType>
          </OJPError>
        </siri:ErrorCondition>
      </OJPTripDelivery>
    </siri:ServiceDelivery>
  </OJPResponse>
</OJP>"""

    const val LOCATIONS =
        """<?xml version="1.0" encoding="UTF-8"?>
<OJP xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri" version="2.0">
  <OJPResponse>
    <siri:ServiceDelivery>
      <siri:ResponseTimestamp>2026-11-21T10:00:00Z</siri:ResponseTimestamp>
      <OJPLocationInformationDelivery>
        <siri:ResponseTimestamp>2026-11-21T10:00:00Z</siri:ResponseTimestamp>
        <siri:DefaultLanguage>de</siri:DefaultLanguage>
        <CalcTime>35</CalcTime>
        <PlaceResult>
          <Place>
            <StopPlace>
              <StopPlaceRef>ch:1:sloid:1120</StopPlaceRef>
              <StopPlaceName><Text xml:lang="de">Lausanne</Text></StopPlaceName>
            </StopPlace>
            <Name><Text xml:lang="de">Lausanne</Text></Name>
            <GeoPosition><siri:Longitude>6.62909</siri:Longitude><siri:Latitude>46.51679</siri:Latitude></GeoPosition>
            <Mode><PtMode>rail</PtMode><siri:RailSubmode>unknown</siri:RailSubmode></Mode>
          </Place>
          <Complete>true</Complete>
          <Probability>1</Probability>
        </PlaceResult>
        <PlaceResult>
          <Place>
            <TopographicPlace>
              <TopographicPlaceCode>23005586:1</TopographicPlaceCode>
              <TopographicPlaceName><Text xml:lang="de">Lausanne (Ort)</Text></TopographicPlaceName>
            </TopographicPlace>
            <Name><Text xml:lang="de">Lausanne (Ort)</Text></Name>
          </Place>
          <Complete>true</Complete>
          <Probability>0.9</Probability>
        </PlaceResult>
        <PlaceResult>
          <Place>
            <StopPlace>
              <StopPlaceRef>8501210</StopPlaceRef>
              <StopPlaceName><Text xml:lang="de">Lausanne-Flon</Text></StopPlaceName>
            </StopPlace>
            <Name><Text xml:lang="de">Lausanne-Flon</Text></Name>
          </Place>
          <Complete>true</Complete>
          <Probability>0.851</Probability>
        </PlaceResult>
      </OJPLocationInformationDelivery>
    </siri:ServiceDelivery>
  </OJPResponse>
</OJP>"""

    const val LOCATIONS_NONE =
        """<?xml version="1.0" encoding="UTF-8"?>
<OJP xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri" version="2.0">
  <OJPResponse>
    <siri:ServiceDelivery>
      <siri:ResponseTimestamp>2026-11-21T10:00:00Z</siri:ResponseTimestamp>
      <OJPLocationInformationDelivery>
        <siri:ResponseTimestamp>2026-11-21T10:00:00Z</siri:ResponseTimestamp>
        <PlaceResult>
          <ErrorCondition><LocationProblemType>LOCATION_NORESULTS</LocationProblemType></ErrorCondition>
        </PlaceResult>
      </OJPLocationInformationDelivery>
    </siri:ServiceDelivery>
  </OJPResponse>
</OJP>"""
}

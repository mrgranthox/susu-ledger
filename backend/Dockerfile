FROM node:18-alpine

WORKDIR /usr/src/app

COPY package*.json ./
RUN npm install --production

COPY . .

ENV PORT=8080
ENV NODE_ENV=production

EXPOSE 8080

CMD ["node", "src/server.js"]
